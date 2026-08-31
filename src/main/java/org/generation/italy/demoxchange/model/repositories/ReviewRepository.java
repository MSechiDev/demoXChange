package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    // Controlla se un autore ha già lasciato una recensione per un dato scambio
    boolean existsByExchangeIdAndAuthorId(Long exchangeId, Long authorId);
}