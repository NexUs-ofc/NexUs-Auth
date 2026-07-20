package com.example.nexusauth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "address")
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String neighborhood;

    @Column(nullable = false, length = 150)
    private String street;

    @Column(nullable = false, length = 10)
    private String number;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 8)
    private String cep;

    @Column(nullable = false, length = 100)
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 2)
    private String state;

    protected Address() {}

    public Address(AddressData data) {
        this.neighborhood = data.neighborhood();
        this.street = data.street();
        this.number = data.number();
        this.cep = data.cep();
        this.city = data.city();
        this.state = data.state().toUpperCase();
    }
}
