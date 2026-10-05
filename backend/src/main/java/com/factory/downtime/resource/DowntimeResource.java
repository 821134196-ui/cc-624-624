package com.factory.downtime.resource;

import com.factory.downtime.dto.DtoMapper;
import com.factory.downtime.dto.Dtos;
import com.factory.downtime.model.*;
import com.factory.downtime.service.DowntimeService;
import com.factory.downtime.service.LossService;
import com.factory.downtime.service.SegmentValidator;
import com.factory.downtime.service.ShiftService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.List;

@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
public class DowntimeResource {

    @Inject
    DowntimeService service;

    @Inject
    LossService lossService;

    @Inject
    ShiftService shiftService;

    @Inject
    DtoMapper mapper;

    public record CreateEventReq(long equipmentId, Instant startTime, Instant endTime,
                                 String operatorName, String note) {
    }

    public record UpdateTimesReq(Instant startTime, Instant endTime) {
    }

    public record SegmentReq(Instant start, Instant end, String reason, String note) {
    }

    public record SaveSegmentsReq(List<SegmentReq> segments) {
    }

    public record ReviewReq(String reviewerName) {
    }

    /** 事件列表；可按状态、班次过滤 */
    @GET
    public List<Dtos.EventDto> list(@QueryParam("status") String status,
                                    @QueryParam("date") String date,
                                    @QueryParam("shift") String shift) {
        List<DowntimeEvent> events;
        if (date != null && shift != null) {
            ShiftService.Shift s = shiftService.shiftOf(date, shift);
            events = DowntimeEvent.list(
                    "startTime >= ?1 and startTime < ?2 order by startTime desc", s.start(), s.end());
        } else {
            events = DowntimeEvent.list("order by startTime desc");
        }
        return events.stream()
                .filter(e -> status == null || e.status.name().equals(status))
                .map(e -> mapper.toDto(e, service.segmentsOf(e.id)))
                .toList();
    }

    @GET
    @Path("/{id}")
    public Dtos.EventDto get(@PathParam("id") long id) {
        DowntimeEvent event = service.requireEvent(id);
        return mapper.toDto(event, service.segmentsOf(id));
    }

    /** 操作员登记停机 */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Dtos.EventDto create(CreateEventReq req) {
        DowntimeEvent event = service.createEvent(req.equipmentId(), req.startTime(),
                req.endTime(), req.operatorName(), req.note());
        return mapper.toDto(event, List.of());
    }

    /** 操作员补登/修改起止时间 */
    @PUT
    @Path("/{id}/times")
    @Consumes(MediaType.APPLICATION_JSON)
    public Dtos.EventDto updateTimes(@PathParam("id") long id, UpdateTimesReq req) {
        DowntimeEvent event = service.updateTimes(id, req.startTime(), req.endTime());
        return mapper.toDto(event, service.segmentsOf(id));
    }

    /** 班长提交时间轴分段（服务端强校验守恒） */
    @PUT
    @Path("/{id}/segments")
    @Consumes(MediaType.APPLICATION_JSON)
    public Dtos.EventDto saveSegments(@PathParam("id") long id, SaveSegmentsReq req) {
        List<SegmentValidator.SegmentInput> inputs = req.segments().stream()
                .map(s -> new SegmentValidator.SegmentInput(
                        s.start(), s.end(), parseReason(s.reason()), s.note()))
                .toList();
        DowntimeEvent event = service.saveSegments(id, inputs);
        return mapper.toDto(event, service.segmentsOf(id));
    }

    /** 班长复核，进入正式统计 */
    @POST
    @Path("/{id}/review")
    @Consumes(MediaType.APPLICATION_JSON)
    public Dtos.EventDto review(@PathParam("id") long id, ReviewReq req) {
        DowntimeEvent event = service.review(id, req.reviewerName());
        return mapper.toDto(event, service.segmentsOf(id));
    }

    /** 退回重新分段 */
    @POST
    @Path("/{id}/reopen")
    public Dtos.EventDto reopen(@PathParam("id") long id) {
        DowntimeEvent event = service.reopen(id);
        return mapper.toDto(event, service.segmentsOf(id));
    }

    /** 标准节拍调整后的重算：生成新核算快照，旧结果保留可查 */
    @POST
    @Path("/{id}/recalculate")
    public Dtos.CalcDto recalculate(@PathParam("id") long id) {
        DowntimeEvent event = service.requireEvent(id);
        return mapper.toDto(lossService.calculate(event));
    }

    /** 核算历史：含每次核算采用的节拍标准与版本 */
    @GET
    @Path("/{id}/calculations")
    public List<Dtos.CalcDto> calculations(@PathParam("id") long id) {
        service.requireEvent(id);
        return lossService.history(id).stream().map(mapper::toDto).toList();
    }

    private static ReasonCode parseReason(String reason) {
        if (reason == null) {
            return null;
        }
        try {
            return ReasonCode.valueOf(reason);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("未知停机原因: " + reason);
        }
    }
}
