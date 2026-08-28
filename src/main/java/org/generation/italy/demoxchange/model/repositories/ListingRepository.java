package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Listing;
import org.generation.italy.demoxchange.model.entities.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    @Query("""
    SELECT l FROM Listing l
    JOIN l.item i
    WHERE l.status = 'attivo'
      AND (CAST(:keyword AS string) IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
      AND (:categoryId IS NULL OR i.category.id = :categoryId)
      AND (:minPrice IS NULL OR i.estimatedValue >= :minPrice)
      AND (:maxPrice IS NULL OR i.estimatedValue <= :maxPrice)
    ORDER BY l.publishedAt DESC
""")
    List<Listing> searchListings(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice
    );


    List<Listing> findByItemOwner(AppUser owner);
    List<Listing> findByStatus(ListingStatus status);
    List<Listing> findByAcceptedCategoriesSlug(String slug);
    boolean existsByItemId(Long itemId);
}
