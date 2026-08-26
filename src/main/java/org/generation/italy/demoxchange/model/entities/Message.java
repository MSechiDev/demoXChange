package org.generation.italy.demoxchange.model.entities;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "messages")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exchange_id", nullable = false)
    private Exchange exchange;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender;

    @Column(nullable = false)
    private String body;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    public Message() {}

    public Message(Exchange exchange, AppUser sender, String body) {
        this.exchange = exchange;
        this.sender = sender;
        this.body = body;
    }

    public Long getId() {
        return id;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public AppUser getSender() {
        return sender;
    }

    public void setSender(AppUser sender) {
        this.sender = sender;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(OffsetDateTime readAt) {
        this.readAt = readAt;
    }
}
