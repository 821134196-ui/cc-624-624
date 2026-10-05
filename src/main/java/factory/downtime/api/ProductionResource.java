package factory.downtime.api;

import factory.downtime.api.dto.Dto;
import factory.downtime.service.ProductionSimulator;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 本地模拟产量回传接口(替代 MES/数采)。
 */
@Path("/api/equipment/{equipmentId}/countback")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProductionResource {

    @Inject
    ProductionSimulator simulator;

    @POST
    @Path("/backfill")
    public Map<String, Object> backfill(@PathParam("equipmentId") Long equipmentId,
                                        @QueryParam("from") String from,
                                        @QueryParam("to") String to) {
        Instant f = Instant.parse(from);
        Instant t = Instant.parse(to);
        int created = simulator.backfill(equipmentId, f, t);
        return Map.of("createdBuckets", created, "source", "SIMULATED");
    }

    @GET
    public List<Dto.CountbackView> list(@PathParam("equipmentId") Long equipmentId,
                                        @QueryParam("from") String from,
                                        @QueryParam("to") String to) {
        return simulator.list(equipmentId, Instant.parse(from), Instant.parse(to)).stream()
                .map(c -> new Dto.CountbackView(c.bucketStart, c.pieces, c.source))
                .toList();
    }
}
