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
}
