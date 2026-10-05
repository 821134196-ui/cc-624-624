package factory.downtime.api;

import factory.downtime.api.dto.Dto;
import factory.downtime.service.ReportService;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;

/**
 * 正式统计报表:仅含已复核事件,损失为估算并带声明。
 */
@Path("/api/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource {

    @Inject
    ReportService reportService;

    @GET
    @Path("/shift")
    public Dto.ReportView shiftReport(@QueryParam("date") String date,
                                      @QueryParam("shift") String shift) {
        String d = (date == null || date.isBlank())
                ? LocalDate.now().toString() : date;
        return reportService.buildShiftReport(d, shift);
    }
}
