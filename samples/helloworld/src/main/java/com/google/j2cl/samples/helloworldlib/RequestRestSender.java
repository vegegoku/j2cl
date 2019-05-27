package com.google.j2cl.samples.helloworldlib;

public interface RequestRestSender<T, S > {
    void send(ServerRequest<T, S> request, ServerRequestCallBack callBack);
}
