package dev.horbatiuk.timecapsule.service.aws;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.aws.InternalAwsException;
import dev.horbatiuk.timecapsule.exception.aws.scheduler.CreateScheduleException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventBridgeScheduledService {

    private static final Logger logger = LoggerFactory.getLogger(EventBridgeScheduledService.class);

    private final SchedulerClient schedulerClient;

    @Value("${aws.lambda-arn}")
    private String lambdaArn;

    @Value("${aws.scheduler-role-arn}")
    private String schedulerRoleArn;

    public void createNewSchedule(UUID capsuleId, Instant openAt) throws CreateScheduleException {
        String scheduleName = capsuleId.toString();
        String scheduleGroup = "default";

        String scheduleExpression = "at(" + DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withZone(ZoneOffset.UTC)
                .format(openAt.truncatedTo(ChronoUnit.SECONDS)) + ")";

        CreateScheduleRequest scheduleRequest = CreateScheduleRequest.builder()
                .name(scheduleName)
                .groupName(scheduleGroup)
                .scheduleExpression(scheduleExpression)
                .actionAfterCompletion(ActionAfterCompletion.DELETE)
                .flexibleTimeWindow(FlexibleTimeWindow.builder()
                        .mode(FlexibleTimeWindowMode.OFF)
                        .build())
                .target(Target.builder()
                        .arn(lambdaArn)
                        .roleArn(schedulerRoleArn)
                        .input("{\"capsuleId\": \"" + capsuleId + "\"}")
                        .build())
                .build();
        try {
            schedulerClient.createSchedule(scheduleRequest);
            logger.info("Schedule created: {} at {}", scheduleName, scheduleExpression);
        } catch (Exception e) {
            logger.error("Failed to create schedule for capsule {}: {}", capsuleId, e.getMessage());
            throw new CreateScheduleException("Failed to create schedule: " + e.getMessage(), e);
        }
    }

    public GetScheduleResponse getSchedule(UUID capsuleId) throws NotFoundException, InternalAwsException {
        String scheduleName = capsuleId.toString();
        String scheduleGroup = "default";

        GetScheduleRequest request = GetScheduleRequest.builder()
                .name(scheduleName)
                .groupName(scheduleGroup)
                .build();
        try {
            return schedulerClient.getSchedule(request);
        } catch (ResourceNotFoundException e) {
            logger.info("Schedule not found: {} at {}", scheduleName, scheduleGroup);
            throw new NotFoundException("Schedule not found: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to get schedule for capsule {}: {}", capsuleId, e.getMessage());
            throw new InternalAwsException("Failed to get schedule: " + e.getMessage(), e);
        }
    }

    public void updateSchedule(
            GetScheduleResponse scheduleResponse,
            boolean isEnable,
            UUID capsuleId
    ) throws NotFoundException {
        if (capsuleId == null) {
            throw new IllegalArgumentException("Capsule ID must not be null");
        }
        if (scheduleResponse == null) {
            throw new IllegalArgumentException("ScheduleResponse must not be null");
        }
        if (scheduleResponse.scheduleExpression() == null || scheduleResponse.scheduleExpression().isBlank()) {
            throw new IllegalArgumentException("ScheduleExpression must not be null or blank");
        }
        if (scheduleResponse.flexibleTimeWindow() == null) {
            throw new IllegalArgumentException("FlexibleTimeWindow must not be null");
        }
        if (scheduleResponse.target() == null) {
            throw new IllegalArgumentException("Target must not be null");
        }

        String scheduleName = capsuleId.toString();
        try {
            UpdateScheduleRequest request = UpdateScheduleRequest.builder()
                    .name(scheduleName)
                    .groupName(scheduleResponse.groupName() != null ? scheduleResponse.groupName() : "default")
                    .scheduleExpression(scheduleResponse.scheduleExpression())
                    .scheduleExpressionTimezone(scheduleResponse.scheduleExpressionTimezone())
                    .startDate(scheduleResponse.startDate())
                    .endDate(scheduleResponse.endDate())
                    .description(scheduleResponse.description())
                    .flexibleTimeWindow(scheduleResponse.flexibleTimeWindow())
                    .target(scheduleResponse.target())
                    .state(isEnable ? ScheduleState.ENABLED : ScheduleState.DISABLED)
                    .build();
            schedulerClient.updateSchedule(request);
        } catch (ResourceNotFoundException e) {
            throw new NotFoundException("Schedule not found while updating", e);
        }
    }

    public void deleteSchedule(UUID capsuleId) throws NotFoundException, InternalAwsException {
        String scheduleName = capsuleId.toString();
        String scheduleGroup = "default";

        DeleteScheduleRequest request = DeleteScheduleRequest.builder()
                .name(scheduleName)
                .groupName(scheduleGroup)
                .build();
        try {
            schedulerClient.deleteSchedule(request);
        } catch (ResourceNotFoundException e) {
            logger.error("Schedule not found for capsule {}: {}", capsuleId, e.getMessage());
            throw new NotFoundException("Schedule not found for capsule: " + capsuleId, e);
        } catch (Exception e) {
            logger.error("Failed to delete schedule for capsule {}: {}", capsuleId, e.getMessage());
            throw new InternalAwsException("Failed to get schedule: " + e.getMessage(), e);
        }
    }
}
