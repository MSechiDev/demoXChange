package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.MessageDto;
import org.generation.italy.demoxchange.model.entities.*;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.MessageRepository;
import org.generation.italy.demoxchange.model.repositories.OfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private OfferRepository offerRepository;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks
    private MessageService messageService;

    private static final long USER_ID = 1L;

    private Message message;

    @BeforeEach
    void setUp() {
        AppUser owner = new AppUser("alice", "hash", null);
        ReflectionTestUtils.setField(owner, "id", USER_ID);

        AppUser sender = new AppUser("bob", "hash", null);
        ReflectionTestUtils.setField(sender, "id", 2L);

        Category category = new Category("Musica", "musica", null);
        Item item = new Item(owner, category, "Chitarra", "descrizione", ItemCondition.buone);
        Listing listing = new Listing(item, "Cagliari");
        Offer offer = new Offer(listing, sender, sender);
        ReflectionTestUtils.setField(offer, "id", 3L);

        message = new Message(offer, sender, "Ciao!");
        ReflectionTestUtils.setField(message, "id", 10L);
    }

    @Test
    void findMine_defaultMode_usesLatestPerThreadQuery() {
        when(messageRepository.findLatestMessagePerThreadForUser(USER_ID)).thenReturn(List.of(message));

        List<MessageDto> result = messageService.findMine(USER_ID, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).senderUsername()).isEqualTo("bob");
        verify(messageRepository, never()).findUnreadForUser(USER_ID);
    }

    @Test
    void findMine_unreadOnly_usesUnreadQuery() {
        when(messageRepository.findUnreadForUser(USER_ID)).thenReturn(List.of(message));

        List<MessageDto> result = messageService.findMine(USER_ID, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        verify(messageRepository, never()).findLatestMessagePerThreadForUser(USER_ID);
    }
}
