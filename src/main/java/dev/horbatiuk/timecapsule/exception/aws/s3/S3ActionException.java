package dev.horbatiuk.timecapsule.exception.aws.s3;

public class S3ActionException extends Exception {
    public S3ActionException(String message) {
        super(message);
    }
    public S3ActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
