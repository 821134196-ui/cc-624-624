package factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 设备台账。
 */
@Entity
@Table(name = "equipment")
public class Equipment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 32)
    public String code;

    @Column(nullable = false, length = 64)
    public String name;

    @Column(length = 64)
    public String workshop;

    public Equipment() {
    }

    public Equipment(String code, String name, String workshop) {
        this.code = code;
        this.name = name;
        this.workshop = workshop;
    }
}
