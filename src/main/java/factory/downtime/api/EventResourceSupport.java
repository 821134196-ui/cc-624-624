package factory.downtime.api;

import factory.downtime.service.EventService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 给资源之间共享的小装配,避免循环依赖感的直接互注入。
 */
@ApplicationScoped
public class EventResourceSupport {

    @Inject
    ViewMapper mapper;

    @Inject
    EventService eventService;

    public ViewMapper mapper() {
        return mapper;
    }

    public EventService events() {
        return eventService;
    }
}
