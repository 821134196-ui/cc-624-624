package factory.downtime.api;

import factory.downtime.api.dto.Dto;
import factory.downtime.service.LossCalculationService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * 按批次 ID 直接读取核算结果。
 * 事件级的批次列表 / 重算路由见 {@link EventResource}(同根 /api/events)。
 */
@Path("/api/calculations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CalculationResource {

    @Inject
    LossCalculationService calcService;

    @Inject
    EventResourceSupport support;

    @GET
    @Path("/{calcId}")
    public Dto.CalcView one(@PathParam("calcId") Long calcId) {
        return support.mapper().calc(calcService.get(calcId));
    }
}
