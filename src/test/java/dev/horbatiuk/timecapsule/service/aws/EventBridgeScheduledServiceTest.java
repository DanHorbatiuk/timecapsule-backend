package dev.horbatiuk.timecapsule.service.aws;

import dev.horbatiuk.timecapsule.exception.NotFoundException;
import dev.horbatiuk.timecapsule.exception.aws.InternalAwsException;
import dev.horbatiuk.timecapsule.exception.aws.scheduler.CreateScheduleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventBridgeScheduledServiceTest {

    private SchedulerClient schedulerClient;
    private EventBridgeScheduledService service;

    private final String lambdaArn = "arn:aws:lambda:region:account-id:function:my-function";
    private final String schedulerRoleArn = "arn:aws:iam::account-id:role/scheduler-role";

    @BeforeEach
    void setUp() {
        schedulerClient = mock(SchedulerClient.class);
        service = new EventBridgeScheduledService(schedulerClient);

        setField(service, "lambdaArn", lambdaArn);
        setField(service, "schedulerRoleArn", schedulerRoleArn);
    }

    @Test
    void createNewSchedule_ShouldCallSchedulerClientWithCorrectRequest() throws CreateScheduleException {
        UUID capsuleId = UUID.randomUUID();
        Instant openAt = Instant.parse("2025-08-01T12:00:00Z");

        service.createNewSchedule(capsuleId, openAt);

        ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
        verify(schedulerClient).createSchedule(captor.capture());

        CreateScheduleRequest req = captor.getValue();

        assertEquals(capsuleId.toString(), req.name());
        assertEquals("default", req.groupName());
        assertEquals("at(2025-08-01T12:00:00)", req.scheduleExpression());
        assertEquals(lambdaArn, req.target().arn());
        assertEquals(schedulerRoleArn, req.target().roleArn());
        assertTrue(req.target().input().contains(capsuleId.toString()));
    }

    @Test
    void createNewSchedule_WhenSchedulerThrows_ShouldThrowCreateScheduleException() {
        UUID capsuleId = UUID.randomUUID();
        Instant openAt = Instant.now();

        doThrow(SchedulerException.builder().message("AWS error").build())
                .when(schedulerClient).createSchedule(any(CreateScheduleRequest.class));

        CreateScheduleException ex = assertThrows(CreateScheduleException.class, () ->
                service.createNewSchedule(capsuleId, openAt));

        assertTrue(ex.getMessage().contains("Failed to create schedule"));
    }

    @Test
    void getSchedule_ShouldReturnResponse() throws NotFoundException, InternalAwsException {
        UUID capsuleId = UUID.randomUUID();

        GetScheduleResponse expectedResponse = GetScheduleResponse.builder()
                .name(capsuleId.toString())
                .groupName("default")
                .build();

        when(schedulerClient.getSchedule(any(GetScheduleRequest.class))).thenReturn(expectedResponse);

        GetScheduleResponse actual = service.getSchedule(capsuleId);
        assertEquals(expectedResponse, actual);
    }

    @Test
    void getSchedule_WhenResourceNotFound_ShouldThrowNotFoundException() {
        UUID capsuleId = UUID.randomUUID();

        when(schedulerClient.getSchedule(any(GetScheduleRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("Not found").build());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> service.getSchedule(capsuleId));
        assertTrue(ex.getMessage().contains("Schedule not found"));
    }

    @Test
    void getSchedule_WhenOtherException_ShouldThrowInternalAwsException() {
        UUID capsuleId = UUID.randomUUID();

        when(schedulerClient.getSchedule(any(GetScheduleRequest.class)))
                .thenThrow(RuntimeException.class);

        InternalAwsException ex = assertThrows(InternalAwsException.class, () -> service.getSchedule(capsuleId));
        assertTrue(ex.getMessage().contains("Failed to get schedule"));
    }

    @Test
    void updateSchedule_ThrowsIllegalArgumentExceptionForNullInputs() {
        UUID capsuleId = UUID.randomUUID();
        GetScheduleResponse scheduleResponse = mock(GetScheduleResponse.class);

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> service.updateSchedule(null, true, capsuleId));
        assertEquals("ScheduleResponse must not be null", ex1.getMessage());

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> service.updateSchedule(scheduleResponse, true, null));
        assertEquals("Capsule ID must not be null", ex2.getMessage());

        when(scheduleResponse.scheduleExpression()).thenReturn(null);
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> service.updateSchedule(scheduleResponse, true, capsuleId));
        assertEquals("ScheduleExpression must not be null or blank", ex3.getMessage());

        when(scheduleResponse.scheduleExpression()).thenReturn("  ");
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class,
                () -> service.updateSchedule(scheduleResponse, true, capsuleId));
        assertEquals("ScheduleExpression must not be null or blank", ex4.getMessage());

        when(scheduleResponse.scheduleExpression()).thenReturn("valid-expression");
        when(scheduleResponse.flexibleTimeWindow()).thenReturn(null);
        IllegalArgumentException ex5 = assertThrows(IllegalArgumentException.class,
                () -> service.updateSchedule(scheduleResponse, true, capsuleId));
        assertEquals("FlexibleTimeWindow must not be null", ex5.getMessage());

        when(scheduleResponse.flexibleTimeWindow()).thenReturn(FlexibleTimeWindow.builder().mode(FlexibleTimeWindowMode.OFF).build());
        when(scheduleResponse.target()).thenReturn(null);
        IllegalArgumentException ex6 = assertThrows(IllegalArgumentException.class,
                () -> service.updateSchedule(scheduleResponse, true, capsuleId));
        assertEquals("Target must not be null", ex6.getMessage());
    }

    @Test
    void deleteSchedule_ShouldCallSchedulerClientDelete() throws NotFoundException, InternalAwsException {
        UUID capsuleId = UUID.randomUUID();

        service.deleteSchedule(capsuleId);

        ArgumentCaptor<DeleteScheduleRequest> captor = ArgumentCaptor.forClass(DeleteScheduleRequest.class);
        verify(schedulerClient).deleteSchedule(captor.capture());

        DeleteScheduleRequest req = captor.getValue();
        assertEquals(capsuleId.toString(), req.name());
        assertEquals("default", req.groupName());
    }

    @Test
    void deleteSchedule_WhenResourceNotFound_ShouldThrowNotFoundException() {
        UUID capsuleId = UUID.randomUUID();

        doThrow(ResourceNotFoundException.builder().message("Not found").build())
                .when(schedulerClient).deleteSchedule(any(DeleteScheduleRequest.class));

        NotFoundException ex = assertThrows(NotFoundException.class, () -> service.deleteSchedule(capsuleId));
        assertTrue(ex.getMessage().contains("Schedule not found for capsule"));
    }

    @Test
    void deleteSchedule_WhenOtherException_ShouldThrowInternalAwsException() {
        UUID capsuleId = UUID.randomUUID();

        doThrow(RuntimeException.class)
                .when(schedulerClient).deleteSchedule(any(DeleteScheduleRequest.class));

        InternalAwsException ex = assertThrows(InternalAwsException.class, () -> service.deleteSchedule(capsuleId));
        assertTrue(ex.getMessage().contains("Failed to get schedule"));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
