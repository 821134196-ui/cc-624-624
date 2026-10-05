package factory.downtime.api;

import factory.downtime.api.dto.Dto;
import factory.downtime.service.StandardService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * 节拍标准版本:查询全部历史版本、发布新版本(旧版自动置 SUPERSEDED)。
 */
@Path("/api/equipment/{equipmentId}/standards")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StandardResource {

    @Inject
    StandardService standardService;

    @Inject
    ViewMapper mapper;

    @GET
    public List<Dto.StandardView> list(@PathParam("equipmentId") Long equipmentId) {
        return standardService.list(equipmentId).stream().map(mapper::standard).toList();
    }

    @POST
    public Dto.StandardView publish(@PathParam("equipmentId") Long equipmentId,
                                    Dto.StandardRequest req) {
        return mapper.standard(standardService.publish(equipmentId, req));
    }
}
