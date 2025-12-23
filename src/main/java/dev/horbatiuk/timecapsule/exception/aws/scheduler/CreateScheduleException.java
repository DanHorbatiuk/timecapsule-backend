package dev.horbatiuk.timecapsule.exception.aws.scheduler;

public class CreateScheduleException extends Exception {
  public CreateScheduleException(String message) {
    super(message);
  }
  public CreateScheduleException(String message, Throwable cause) {
    super(message, cause);
  }
}
