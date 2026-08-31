package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExchangeRepository extends JpaRepository<Exchange, Long> {

    @Query("""
            select e from Exchange e
            where e.offer.offerer.id = :userId or e.offer.listing.item.owner.id = :userId
            order by e.createdAt desc
            """)
    List<Exchange> findAllForUser(@Param("userId") long userId);
}
