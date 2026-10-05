package factory.downtime.api;

import factory.downtime.api.dto.Dto;
import factory.downtime.model.CalcTrigger;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeSegment;
import factory.downtime.model.EventStatus;
import factory.downtime.model.LossCalculation;
import factory.downtime.service.ConservationService;
import factory.downtime.service.EventService;
import factory.downtime.service.LossCalculationService;
import factory.downtime.service.ReviewService;
import factory.downtime.service.SegmentService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Comparator;
import java.util.List;

/**
 * 停机事件:登记、恢复、时间轴分段、守恒检查、复核、连续性视图。
 */
@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    EventService eventService;

    @Inject
    SegmentService segmentService;

    @Inject
    ReviewService reviewService;

    @Inject
    LossCalculationService calcService;

    @Inject
    ConservationService conservation;

    @Inject
    ViewMapper mapper;

    @GET
    public List<Dto.EventView> list() {
        return eventService.listAllEvents().stream()
                .sorted(Comparator.comparing((DowntimeEvent e) -> e.startTime).reversed())
                .map(e -> mapper.event(e,
                        DowntimeSegment.list("eventId = ?1 order by ordinal", e.id)))
                .toList();
    }

    @GET
    @Path("/{id}")
    public Dto.EventView get(@PathParam("id") Long id) {
        DowntimeEvent e = eventService.get(id);
        return mapper.event(e, segmentService.list(id));
    }

    @POST
    public Dto.EventView register(Dto.RegisterEventRequest req) {
        DowntimeEvent e = eventService.register(req);
        return mapper.event(e, List.of());
    }

    /** 设备恢复(可跨班),回填同一事件。 */
    @POST
    @Path("/{id}/resolve")
    public Dto.EventView resolve(@PathParam("id") Long id, Dto.ResolveEventRequest req) {
        DowntimeEvent e = eventService.resolve(id, req);
        return mapper.event(e, segmentService.list(id));
    }

    /** 时间轴分段保存(全量替换,强制守恒)。 */
    @POST
    @Path("/{id}/segments")
    public Dto.EventView saveSegments(@PathParam("id") Long id, Dto.SaveSegmentsRequest req) {
        DowntimeEvent e = segmentService.saveSegments(id, req);
        return mapper.event(e, segmentService.list(id));
    }

    /** 仅检查守恒,不保存 —— 供前端实时提示。 */
    @GET
    @Path("/{id}/conservation")
    public Dto.ConservationView conservation(@PathParam("id") Long id) {
        DowntimeEvent e = eventService.get(id);
        return conservation.inspect(e, segmentService.list(id));
    }

    /** 跨班连续性视图。 */
    @GET
    @Path("/{id}/continuity")
    public Dto.ContinuityView continuity(@PathParam("id") Long id) {
        return eventService.continuity(eventService.get(id));
    }

    /** 班长复核通过 -> 自动按当班标准首算 -> 进入正式统计。 */
    @POST
    @Path("/{id}/review")
    public Dto.EventView review(@PathParam("id") Long id, Dto.ReviewRequest req) {
        DowntimeEvent e = reviewService.review(id, req.reviewedBy(), req.comment());
        return mapper.event(e, segmentService.list(id));
    }

    @POST
    @Path("/{id}/reject")
    public Dto.EventView reject(@PathParam("id") Long id, Dto.ReviewRequest req) {
        DowntimeEvent e = reviewService.reject(id, req.reviewedBy(), req.comment());
        return mapper.event(e, segmentService.list(id));
    }

    /** 损失核算批次(首算 + 历次重算,全部保留)。 */
    @GET
    @Path("/{id}/calculations")
    public List<Dto.CalcView> batches(@PathParam("id") Long id) {
        return calcService.listBatches(id).stream().map(mapper::calc).toList();
    }

    /**
     * 标准变更后重新计算:新增批次,原批次保留并标记已取代。
     * 仅已复核事件可重算。
     */
    @POST
    @Path("/{id}/recalculate")
    public Dto.CalcView recalculate(@PathParam("id") Long id, Dto.RecalcRequest req) {
        DowntimeEvent event = eventService.get(id);
        if (event.status != EventStatus.REVIEWED) {
            throw BusinessException.of("只有已复核事件才能重算;未复核事件请先完成复核(复核时自动首算)");
        }
        String mode = req.mode() == null ? "CURRENT" : req.mode();
        LossCalculation calc = calcService.calculate(
                id, CalcTrigger.RECALC, mode, req.calculatedBy());
        return mapper.calc(calc);
    }

    @DELETE
    @Path("/{id}")
    public java.util.Map<String, Object> delete(@PathParam("id") Long id) {
        eventService.deleteEvent(id);
        return java.util.Map.of("deleted", id);
    }
}
