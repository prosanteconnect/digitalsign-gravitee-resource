//package fr.ans.psc;
//
//public class DigitalSignResponse {
//    private final boolean success;
//    private final String payload;
//    private final Throwable throwable;
//
//    public DigitalSignResponse(final boolean success, final String payload) {
//        this.success = success;
//        this.payload = payload;
//        this.throwable = null;
//    }
//
//    public DigitalSignResponse(final Throwable throwable) {
//        this.success = false;
//        this.throwable = throwable;
//        this.payload = throwable.getMessage();
//    }
//
//    public boolean isSuccess() {
//        return success;
//    }
//
//    public String getPayload() {
//        return payload;
//    }
//
//    public Throwable getThrowable() {
//        return throwable;
//    }
//}
