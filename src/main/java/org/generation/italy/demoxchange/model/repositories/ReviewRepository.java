package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
}

