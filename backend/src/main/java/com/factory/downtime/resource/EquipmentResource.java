package com.factory.downtime.resource;

import com.factory.downtime.dto.DtoMapper;
import com.factory.downtime.dto.Dtos;
import com.factory.downtime.model.Equipment;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/equipment")
@Produces(MediaType.APPLICATION_JSON)
public class EquipmentResource {

    @Inject
    DtoMapper mapper;

    @GET
    public List<Dtos.EquipmentDto> list() {
        return Equipment.<Equipment>listAll().stream().map(mapper::toDto).toList();
    }
}
