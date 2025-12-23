package dev.horbatiuk.timecapsule.exception.aws;

public class InternalAwsException extends Exception {
    public InternalAwsException(String message) {
        super(message);
    }
    public InternalAwsException(String message, Throwable cause) {
    super(message, cause);
  }
}
