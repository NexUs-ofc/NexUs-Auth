package com.example.nexusauth.model;

import jakarta.persistence.Column;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "auth_method")
public class AuthMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "auth_provider_enum")
    private AuthProvider provider;

    @Column(nullable = false, length = 255)
    private String credential;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuthMethod() {}

    public AuthMethod(Profile profile, AuthProvider provider, String credential) {
        this.profile = profile;
        this.provider = provider;
        this.credential = credential;
    }

    public AuthMethod(long profileId, AuthProvider provider, String credential) {
        this(new Profile(profileId, null, null, null, null), provider, credential);
    }

    public int profileId() {
        return profile.id();
    }

    public AuthProvider provider() {
        return provider;
    }

    public String credential() {
        return credential;
    }

    public void updateCredential(String credential) {
        this.credential = credential;
    }
}
