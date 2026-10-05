package com.factory.downtime.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "equipment")
public class Equipment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 64)
    public String code;

    @Column(nullable = false, length = 128)
    public String name;

    public Equipment() {
    }

    public Equipment(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
