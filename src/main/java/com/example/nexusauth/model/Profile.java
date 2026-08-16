package com.example.nexusauth.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "profile")
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "address_id")
    private Address address;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "profile_type_enum")
    private ProfileType type;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "profile_status_enum")
    private ProfileStatus status;

    @ElementCollection
    @CollectionTable(name = "profile_phone", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "phone", nullable = false, length = 16)
    private Set<String> phones = new LinkedHashSet<>();

    protected Profile() {}

    public Profile(Address address, String email, String name, ProfileType type,
                   String profileImageUrl, List<String> phones) {
        this.address = address;
        this.email = email;
        this.name = name;
        this.type = type;
        this.profileImageUrl = profileImageUrl;
        this.status = ProfileStatus.ACTIVE;
        this.phones.addAll(phones);
    }

    public Profile(long id, String email, String name, ProfileType type, ProfileStatus status) {
        this.id = Math.toIntExact(id);
        this.email = email;
        this.name = name;
        this.type = type;
        this.status = status;
    }

    public int id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public ProfileType type() {
        return type;
    }

    public ProfileStatus status() {
        return status;
    }
}
