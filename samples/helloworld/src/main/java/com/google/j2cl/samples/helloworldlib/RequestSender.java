package com.google.j2cl.samples.helloworldlib;


public abstract class RequestSender<R, S> implements RequestRestSender<R, S> {

    @Override
    public void send(ServerRequest<R, S> request, ServerRequestCallBack callBack) {

    }
}

