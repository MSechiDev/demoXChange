package org.generation.italy.demoxchange.model.entities;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "exchanges")
public class Exchange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false, unique = true)
    private Offer offer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExchangeStatus status = ExchangeStatus.in_corso;

    @Column(name = "owner_confirmed_at")
    private OffsetDateTime ownerConfirmedAt;

    @Column(name = "offerer_confirmed_at")
    private OffsetDateTime offererConfirmedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExchangeMethod method;

    @Column(name = "logistics_confirmed_by_owner", nullable = false)
    private boolean logisticsConfirmedByOwner = false;

    @Column(name = "logistics_confirmed_by_offerer", nullable = false)
    private boolean logisticsConfirmedByOfferer = false;

    @Version
    @Column(nullable = false)
    private long version;

    public Exchange() {}

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Exchange(Offer offer) {
        this.offer = offer;
    }

    public Long getId() {
        return id;
    }

    public Offer getOffer() {
        return offer;
    }

    public void setOffer(Offer offer) {
        this.offer = offer;
    }

    public ExchangeStatus getStatus() {
        return status;
    }

    public void setStatus(ExchangeStatus status) {
        this.status = status;
    }

    public OffsetDateTime getOwnerConfirmedAt() {
        return ownerConfirmedAt;
    }

    public void setOwnerConfirmedAt(OffsetDateTime ownerConfirmedAt) {
        this.ownerConfirmedAt = ownerConfirmedAt;
    }

    public OffsetDateTime getOffererConfirmedAt() {
        return offererConfirmedAt;
    }

    public void setOffererConfirmedAt(OffsetDateTime offererConfirmedAt) {
        this.offererConfirmedAt = offererConfirmedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public ExchangeMethod getMethod() {
        return method;
    }

    public void setMethod(ExchangeMethod method) {
        this.method = method;
    }

    public boolean isLogisticsConfirmedByOwner() {
        return logisticsConfirmedByOwner;
    }

    public void setLogisticsConfirmedByOwner(boolean logisticsConfirmedByOwner) {
        this.logisticsConfirmedByOwner = logisticsConfirmedByOwner;
    }

    public boolean isLogisticsConfirmedByOfferer() {
        return logisticsConfirmedByOfferer;
    }

    public void setLogisticsConfirmedByOfferer(boolean logisticsConfirmedByOfferer) {
        this.logisticsConfirmedByOfferer = logisticsConfirmedByOfferer;
    }
}
