package factory.downtime.api;

import factory.downtime.api.dto.Dto;
import factory.downtime.model.Equipment;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/equipment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EquipmentResource {

    @Inject
    ViewMapper mapper;

    @GET
    public List<Dto.EquipmentView> list() {
        return Equipment.<Equipment>listAll().stream().map(mapper::equipment).toList();
    }

    @POST
    @Transactional
    public Dto.EquipmentView create(Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            throw BusinessException.of("设备编码必填");
        }
        if (Equipment.count("code", code) > 0) {
            throw BusinessException.of("设备编码已存在: " + code);
        }
        Equipment e = new Equipment(code.trim(), body.get("name"), body.get("workshop"));
        e.persist();
        return mapper.equipment(e);
    }
}
