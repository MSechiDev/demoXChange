package org.generation.italy.demoxchange.services;

import org.generation.italy.demoxchange.model.dto.CreateReviewDto;
import org.generation.italy.demoxchange.model.dto.ReviewSummaryDto;
import org.generation.italy.demoxchange.model.entities.AppUser;
import org.generation.italy.demoxchange.model.entities.Exchange;
import org.generation.italy.demoxchange.model.entities.ExchangeStatus;
import org.generation.italy.demoxchange.model.entities.Review;
import org.generation.italy.demoxchange.model.exceptions.BadRequestException;
import org.generation.italy.demoxchange.model.exceptions.ConflictException;
import org.generation.italy.demoxchange.model.exceptions.ForbiddenException;
import org.generation.italy.demoxchange.model.exceptions.NotFoundException;
import org.generation.italy.demoxchange.model.repositories.AppUserRepository;
import org.generation.italy.demoxchange.model.repositories.ExchangeRepository;
import org.generation.italy.demoxchange.model.repositories.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ExchangeRepository exchangeRepository;
    private final AppUserRepository appUserRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ExchangeRepository exchangeRepository,
                         AppUserRepository appUserRepository) {
        this.reviewRepository = reviewRepository;
        this.exchangeRepository = exchangeRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public ReviewSummaryDto createReview(CreateReviewDto dto, String currentUsername) {

        // 1. Recupera l'autore dal SecurityContext (username da JWT)
        AppUser author = appUserRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new NotFoundException("user_not_found", "Utente non trovato"));

        // 2. Recupera lo scambio
        Exchange exchange = exchangeRepository.findById(dto.exchangeId())
                .orElseThrow(() -> new NotFoundException("exchange_not_found", "Scambio non trovato"));

        // 3. Controllo stato dello scambio
        if (exchange.getStatus() != ExchangeStatus.completato) {
            throw new BadRequestException("exchange_not_completed",
                    "Impossibile lasciare una recensione: lo scambio non è ancora completato.");
        }

        // 4. Estrazione delle due parti coinvolte nello scambio.
        //    NIENTE try-catch "silenzioso": se manca un dato collegato,
        //    è un problema di integrità che va segnalato chiaramente,
        //    non nascosto con un valore null passato avanti. Restano IllegalStateException
        //    (quindi 500) di proposito: non sono errori di input dell'utente ma corruzione dati.
        if (exchange.getOffer() == null) {
            throw new IllegalStateException(
                    "Dati incoerenti: lo scambio " + exchange.getId() + " non ha un'offerta collegata.");
        }

        AppUser offerer = exchange.getOffer().getOfferer();

        if (exchange.getOffer().getListing() == null
                || exchange.getOffer().getListing().getItem() == null) {
            throw new IllegalStateException(
                    "Dati incoerenti: impossibile risalire al proprietario dell'item per lo scambio "
                            + exchange.getId() + ".");
        }

        AppUser owner = exchange.getOffer().getListing().getItem().getOwner();

        // 5. Determinazione del destinatario in base a chi è l'autore.
        //    L'autore DEVE essere una delle due parti coinvolte: se non lo è,
        //    non è autorizzato a recensire questo scambio.
        AppUser recipient;
        if (author.getId().equals(offerer.getId())) {
            recipient = owner;
        } else if (author.getId().equals(owner.getId())) {
            recipient = offerer;
        } else {
            // L'utente autenticato non fa parte di questo scambio: 403, non un fallback a caso.
            throw new ForbiddenException("not_participant",
                    "Non sei tra i partecipanti di questo scambio: non puoi lasciare una recensione.");
        }

        // Guardia di sicurezza extra: non dovrebbe mai accadere se i dati sono coerenti
        // (offerer e owner sono per definizione persone diverse), ma meglio essere espliciti.
        if (recipient.getId().equals(author.getId())) {
            throw new BadRequestException("cannot_review_self", "Non puoi recensire te stesso.");
        }

        // 6. Verifica recensione duplicata
        if (reviewRepository.existsByExchangeIdAndAuthorId(exchange.getId(), author.getId())) {
            throw new ConflictException("review_already_exists", "Hai già rilasciato una recensione per questo scambio.");
        }

        // 7. Salvataggio
        Review review = new Review(exchange, author, recipient, dto.rating());
        review.setComment(dto.comment());
        Review saved = reviewRepository.save(review);

        return toDto(saved);
    }

    private ReviewSummaryDto toDto(Review review) {
        return new ReviewSummaryDto(
                review.getId(),
                review.getAuthor().getId(),
                review.getAuthor().getUsername(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
