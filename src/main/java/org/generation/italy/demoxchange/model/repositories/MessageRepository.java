package org.generation.italy.demoxchange.model.repositories;

import org.generation.italy.demoxchange.model.entities.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByOfferIdOrderBySentAtAsc(Long offerId);

    @Query("""
            select m from Message m
            where m.sentAt = (
                select max(m2.sentAt) from Message m2 where m2.offer.id = m.offer.id
            )
            and (m.offer.offerer.id = :userId or m.offer.listing.item.owner.id = :userId)
            order by m.sentAt desc
            """)
    List<Message> findLatestMessagePerThreadForUser(@Param("userId") long userId);

    @Query("""
            select m from Message m
            where m.readAt is null
              and m.sender.id <> :userId
              and (m.offer.offerer.id = :userId or m.offer.listing.item.owner.id = :userId)
            order by m.sentAt asc
            """)
    List<Message> findUnreadForUser(@Param("userId") long userId);

    @Modifying
    @Query("""
            update Message m
            set m.readAt = :now
            where m.offer.id = :offerId
              and m.sender.id <> :userId
              and m.readAt is null
            """)
    int markThreadAsRead(@Param("offerId") long offerId, @Param("userId") long userId, @Param("now") OffsetDateTime now);
}
