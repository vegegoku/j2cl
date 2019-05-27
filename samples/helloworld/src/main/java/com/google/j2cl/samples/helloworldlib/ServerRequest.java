package com.google.j2cl.samples.helloworldlib;

import java.util.function.Supplier;

public class ServerRequest<R, S> {

    private SenderSupplier senderSupplier = new SenderSupplier(new Supplier<RequestRestSender>() {
        @Override
        public RequestRestSender get() {
            return new RequestSender<R, S>() {
            };
        }
    });


}
