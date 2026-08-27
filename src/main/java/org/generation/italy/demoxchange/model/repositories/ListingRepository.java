package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    @Query("SELECT l FROM Listing l " +
            "WHERE l.status = org.generation.italy.demoxchange.model.entities.ListingStatus.attivo " +
            "AND (:keyword IS NULL OR LOWER(l.item.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:categoryId IS NULL OR l.item.category.id = :categoryId) " +
            "AND (:minPrice IS NULL OR l.item.estimatedValue >= :minPrice)" +
            "AND (:maxPrice IS NULL OR l.item.estimatedValue <= :maxPrice)")
    List<Listing> searchListings(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );
}
