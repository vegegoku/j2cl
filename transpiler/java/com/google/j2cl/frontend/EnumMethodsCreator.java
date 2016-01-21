package com.google.j2cl.frontend;

import com.google.common.base.Preconditions;
import com.google.j2cl.ast.ArrayLiteral;
import com.google.j2cl.ast.ArrayTypeDescriptor;
import com.google.j2cl.ast.BinaryExpression;
import com.google.j2cl.ast.BinaryOperator;
import com.google.j2cl.ast.Block;
import com.google.j2cl.ast.Expression;
import com.google.j2cl.ast.ExpressionStatement;
import com.google.j2cl.ast.Field;
import com.google.j2cl.ast.FieldAccess;
import com.google.j2cl.ast.FieldBuilder;
import com.google.j2cl.ast.FieldDescriptor;
import com.google.j2cl.ast.FieldDescriptorBuilder;
import com.google.j2cl.ast.IfStatement;
import com.google.j2cl.ast.JavaType;
import com.google.j2cl.ast.JsInfo;
import com.google.j2cl.ast.JsMemberType;
import com.google.j2cl.ast.Method;
import com.google.j2cl.ast.MethodCall;
import com.google.j2cl.ast.MethodDescriptor;
import com.google.j2cl.ast.MethodDescriptorBuilder;
import com.google.j2cl.ast.NullLiteral;
import com.google.j2cl.ast.ReturnStatement;
import com.google.j2cl.ast.Statement;
import com.google.j2cl.ast.TypeDescriptor;
import com.google.j2cl.ast.TypeDescriptors;
import com.google.j2cl.ast.TypeDescriptors.BootstrapType;
import com.google.j2cl.ast.Variable;
import com.google.j2cl.ast.Visibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class generates the ast structure for the synthesized static methods values and valueOf on
 * Enum types.  Additionally, we add a private static field "namesToValuesMap" which is created
 * the first time valueOf() is called and allows for quick lookup of Enum values by (String) name.
 */
public class EnumMethodsCreator {
  private static final String VALUE_OF_METHOD_NAME = "valueOf";
  private static final String VALUES_METHOD_NAME = "values";
  private static final String NAMES_TO_VALUES_MAP_FIELD_NAME = "namesToValuesMap";
  private static final String CREATE_MAP_METHOD_NAME = "createMapFromValues";
  private static final String GET_VALUE_METHOD_NAME = "getValueFromNameAndMap";

  private JavaType enumType;
  private FieldDescriptor namesToValuesMapFieldDescriptor;
  private MethodDescriptor valuesMethodDescriptor;
  private MethodDescriptor valueOfMethodDescriptor;

  public static void applyTo(JavaType enumType) {
    Preconditions.checkArgument(enumType.isEnum());
    EnumMethodsCreator instance = new EnumMethodsCreator(enumType);
    instance.run();
  }

  private EnumMethodsCreator(JavaType enumType) {
    boolean jsType = enumType.getDescriptor().isJsType();

    this.enumType = enumType;
    this.namesToValuesMapFieldDescriptor =
        FieldDescriptorBuilder.fromDefault(
                enumType.getDescriptor(),
                NAMES_TO_VALUES_MAP_FIELD_NAME,
                TypeDescriptors.get().javaLangObject)
            .isStatic(true)
            .visibility(Visibility.PRIVATE)
            .build();
    this.valuesMethodDescriptor =
        MethodDescriptorBuilder.fromDefault()
            .isRaw(jsType)
            .isStatic(true)
            .enclosingClassTypeDescriptor(enumType.getDescriptor())
            .methodName(VALUES_METHOD_NAME)
            .returnTypeDescriptor(enumType.getDescriptor().getForArray(1))
            .parameterTypeDescriptors(Arrays.asList(new TypeDescriptor[0]))
            .jsInfo(jsType ? JsInfo.create(JsMemberType.METHOD, null, null, false) : JsInfo.NONE)
            .build();
    this.valueOfMethodDescriptor =
        MethodDescriptorBuilder.fromDefault()
            .isRaw(jsType)
            .isStatic(true)
            .enclosingClassTypeDescriptor(enumType.getDescriptor())
            .methodName(VALUE_OF_METHOD_NAME)
            .returnTypeDescriptor(enumType.getDescriptor())
            .parameterTypeDescriptors(Arrays.asList(TypeDescriptors.get().javaLangString))
            .jsInfo(jsType ? JsInfo.create(JsMemberType.METHOD, null, null, false) : JsInfo.NONE)
            .build();
  }

  private void run() {
    enumType.addField(
        FieldBuilder.fromDefault(this.namesToValuesMapFieldDescriptor)
            .initializer(NullLiteral.NULL)
            .build());
    enumType.addMethod(createValueOfMethod());
    enumType.addMethod(createValuesMethod());
  }

  /**
   * Creates the ast needed for valueOf(String name) which is of the form:
   *
   * private Object namesToValuesMap = null;
   * public static EnumType valueOf(String name) {
   *   if(namesToValuesMap == null){
   *     namesToValuesMap = Enums.createMapFromValues(this.values());
   *   }
   *   return Enums.getValueFromNameAndMap(name, namesToValuesMap);
   * }
   */
  private Method createValueOfMethod() {
    Variable nameParameter =
        new Variable("name", TypeDescriptors.get().javaLangString, false, true);
    MethodDescriptor createMapMethodDescriptor =
        MethodDescriptorBuilder.fromDefault()
            .isRaw(true)
            .isStatic(true)
            .enclosingClassTypeDescriptor(BootstrapType.ENUMS.getDescriptor())
            .methodName(CREATE_MAP_METHOD_NAME)
            .returnTypeDescriptor(namesToValuesMapFieldDescriptor.getTypeDescriptor())
            .parameterTypeDescriptors(Arrays.asList(enumType.getDescriptor()))
            .build();
    MethodDescriptor getMethodDescriptor =
        MethodDescriptorBuilder.fromDefault()
            .isRaw(true)
            .isStatic(true)
            .enclosingClassTypeDescriptor(BootstrapType.ENUMS.getDescriptor())
            .methodName(GET_VALUE_METHOD_NAME)
            .returnTypeDescriptor(enumType.getDescriptor())
            .parameterTypeDescriptors(
                Arrays.asList(
                    nameParameter.getTypeDescriptor(),
                    namesToValuesMapFieldDescriptor.getTypeDescriptor()))
            .build();

    Expression nameParameterAccess = nameParameter.getReference();
    Expression namesToValuesMapFieldAccess =
        new FieldAccess(null, this.namesToValuesMapFieldDescriptor);

    // If statement
    Expression namesToValuesMapIsNullComparison =
        new BinaryExpression(
            TypeDescriptors.get().primitiveBoolean,
            namesToValuesMapFieldAccess,
            BinaryOperator.EQUALS,
            NullLiteral.NULL);
    Expression valuesCall = MethodCall.createRegularMethodCall(null, valuesMethodDescriptor);

    Expression createMapCall =
        MethodCall.createRegularMethodCall(null, createMapMethodDescriptor, valuesCall);
    Expression assignMapCallToField =
        new BinaryExpression(
            namesToValuesMapFieldAccess.getTypeDescriptor(),
            namesToValuesMapFieldAccess,
            BinaryOperator.ASSIGN,
            createMapCall);
    Statement thenStatement = new ExpressionStatement(assignMapCallToField);
    Block thenBlock = new Block(Arrays.asList(thenStatement));
    Statement ifStatement = new IfStatement(namesToValuesMapIsNullComparison, thenBlock, null);

    // Return statement
    Expression getMethodCall =
        MethodCall.createRegularMethodCall(
            null, getMethodDescriptor, nameParameterAccess, namesToValuesMapFieldAccess);
    Statement returnStatement =
        new ReturnStatement(getMethodCall, enumType.getDescriptor().getForArray(1));

    List<Statement> blockStatements = new ArrayList<>();
    blockStatements.add(ifStatement);
    blockStatements.add(returnStatement);
    Block body = new Block(blockStatements);
    return new Method(valueOfMethodDescriptor, Arrays.asList(nameParameter), body);
  }

  /**
   * Creates the ast needed for values() which is of the form:
   *
   * static EnumType[] values() {
   *   return [
   *     EnumType.VALUE1,
   *     EnumType.VALUE2 ...
   *   ];
   * }
   */
  private Method createValuesMethod() {
    // Create method body.
    List<Expression> values = new ArrayList<>();
    for (Field enumField : enumType.getEnumFields()) {
      values.add(new FieldAccess(null, enumField.getDescriptor()));
    }
    Expression arrayOfValues =
        new ArrayLiteral((ArrayTypeDescriptor) enumType.getDescriptor().getForArray(1), values);
    Statement returnStatement =
        new ReturnStatement(arrayOfValues, enumType.getDescriptor().getForArray(1));
    List<Statement> blockStatements = new ArrayList<>();
    blockStatements.add(returnStatement);
    Block body = new Block(blockStatements);
    return new Method(valuesMethodDescriptor, Arrays.asList(new Variable[0]), body);
  }
}