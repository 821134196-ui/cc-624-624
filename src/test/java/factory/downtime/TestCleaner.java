package factory.downtime;

import factory.downtime.model.CycleTimeStandard;
import factory.downtime.model.DowntimeEvent;
import factory.downtime.model.DowntimeSegment;
import factory.downtime.model.Equipment;
import factory.downtime.model.LossCalculation;
import factory.downtime.model.LossLine;
import factory.downtime.model.ProductionCountback;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestCleaner {

    @Transactional
    public void wipe() {
        LossLine.deleteAll();
        LossCalculation.deleteAll();
        DowntimeSegment.deleteAll();
        DowntimeEvent.deleteAll();
        CycleTimeStandard.deleteAll();
        ProductionCountback.deleteAll();
        Equipment.deleteAll();
    }
}
