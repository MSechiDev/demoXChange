package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Item;
import org.generation.italy.demoxchange.model.entities.ItemCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByIdAndOwnerId(Long id, Long ownerId);

    @Query("""
            select i from Item i
            where i.owner.id = :ownerId
              and (:categoryId is null or i.category.id = :categoryId)
              and (:condition is null or i.itemCondition = :condition)
              and (:includeArchived = true or i.archived = false)
              and (:minValue is null or (i.estimatedValue is not null and i.estimatedValue >= :minValue))
              and (:maxValue is null or (i.estimatedValue is not null and i.estimatedValue <= :maxValue))
              and (
                    :q is null
                    or lower(i.title) like lower(concat('%', :q, '%'))
                    or lower(i.description) like lower(concat('%', :q, '%'))
              )
            order by i.createdAt desc
            """)
    List<Item> search(
            @Param("ownerId") long ownerId,
            @Param("categoryId") Long categoryId,
            @Param("condition") ItemCondition condition,
            @Param("includeArchived") boolean includeArchived,
            @Param("minValue") BigDecimal minValue,
            @Param("maxValue") BigDecimal maxValue,
            @Param("q") String q
    );
}
