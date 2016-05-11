/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.ast.visitors;

import com.google.j2cl.ast.AbstractRewriter;
import com.google.j2cl.ast.BinaryExpression;
import com.google.j2cl.ast.CatchClause;
import com.google.j2cl.ast.CompilationUnit;
import com.google.j2cl.ast.Expression;
import com.google.j2cl.ast.ExpressionStatement;
import com.google.j2cl.ast.JsInfo;
import com.google.j2cl.ast.MethodCall;
import com.google.j2cl.ast.MethodDescriptor;
import com.google.j2cl.ast.Node;
import com.google.j2cl.ast.ThrowStatement;
import com.google.j2cl.ast.TypeDescriptors;
import com.google.j2cl.ast.TypeDescriptors.BootstrapType;
import com.google.j2cl.ast.Variable;

/**
 * Adds Java Throwable to JavaScript Error conversion.
 * <p> This pass makes sure that exceptions work well across Java/JavaScript boundary.
 * Instead of throwing subclasses of Java Throwable class in generated throw statements, we
 * basically throw the backing JavaScript error object so the generated code looks like this:
 * <pre>{@code
 *   try {
 *     throw $Exceptions.toJs(new SomeJavaException);
 *   } catch(e) {
 *     e = $Exceptions.toJava(e);
 *     if (SomeJavaException.$isInstance(e)) {
 *       // catch block for SomeJavaException
 *     } else {
 *       throw $Exception.toJs(e);
 *     }
 *   }
 * }</pre>
 *
 * <p> As the propagated thrown object is converted to real JavaScript error, it plays better with
 * the browser dev tools (doesn't work well with custom error objects) and callers from JavaScript
 * side.
 */
public class InsertExceptionConversions extends AbstractRewriter {

  public static void applyTo(CompilationUnit compilationUnit) {
    compilationUnit.accept(new InsertExceptionConversions());
  }

  @Override
  public Node rewriteCatchClause(CatchClause catchClause) {
    Variable mainVariable =
        new Variable(
            catchClause.getExceptionVar().getName(),
            TypeDescriptors.get().javaLangObject,
            false,
            false);

    MethodDescriptor toJava =
        MethodDescriptor.Builder.fromDefault()
            .setJsInfo(JsInfo.RAW)
            .setIsStatic(true)
            .setEnclosingClassTypeDescriptor(BootstrapType.EXCEPTIONS.getDescriptor())
            .setMethodName("toJava")
            .setParameterTypeDescriptors(TypeDescriptors.get().javaLangObject)
            .setReturnTypeDescriptor(TypeDescriptors.get().javaLangThrowable)
            .build();

    MethodCall toJavaCall = MethodCall.createMethodCall(null, toJava, mainVariable.getReference());

    Expression assignment =
        BinaryExpression.Builder.asAssignmentTo(mainVariable).setRightOperand(toJavaCall).build();

    catchClause.getBody().getStatements().add(0, new ExpressionStatement(assignment));

    return catchClause;
  }

  @Override
  public Node rewriteThrowStatement(ThrowStatement originalStatement) {
    MethodDescriptor toJs =
        MethodDescriptor.Builder.fromDefault()
            .setJsInfo(JsInfo.RAW)
            .setIsStatic(true)
            .setEnclosingClassTypeDescriptor(BootstrapType.EXCEPTIONS.getDescriptor())
            .setMethodName("toJs")
            .setParameterTypeDescriptors(TypeDescriptors.get().javaLangThrowable)
            .setReturnTypeDescriptor(TypeDescriptors.get().javaLangObject)
            .build();

    MethodCall toJsCall =
        MethodCall.createMethodCall(null, toJs, originalStatement.getExpression());

    return new ThrowStatement(toJsCall);
  }
}
