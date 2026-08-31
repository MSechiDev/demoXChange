--
-- PostgreSQL database dump
--

-- Dumped from database version 18.4 (Debian 18.4-1.pgdg13+1)
-- Dumped by pg_dump version 18.4 (Debian 18.4-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user_roles (
    user_id bigint NOT NULL,
    role character varying(20) NOT NULL
);


--
-- Name: app_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_users (
    user_id bigint NOT NULL,
    username character varying(70) NOT NULL,
    password_hash character varying(256) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    email character varying(254),
    CONSTRAINT ck_app_users_email_format CHECK (((email IS NULL) OR ((email)::text ~* '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'::text))),
    CONSTRAINT ck_app_users_password_hash_not_blank CHECK ((btrim((password_hash)::text) <> ''::text)),
    CONSTRAINT ck_app_users_username_not_blank CHECK ((btrim((username)::text) <> ''::text))
);


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id bigint NOT NULL,
    name character varying(60) NOT NULL,
    slug character varying(60) NOT NULL,
    description character varying(255),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_categories_name_not_blank CHECK ((btrim((name)::text) <> ''::text)),
    CONSTRAINT ck_categories_slug_format CHECK (((slug)::text ~ '^[a-z0-9]+(-[a-z0-9]+)*$'::text))
);


--
-- Name: TABLE categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.categories IS 'Categorie fisse gestite dagli amministratori. Non eliminare: usare is_active.';


--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.categories ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: exchanges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exchanges (
    id bigint NOT NULL,
    offer_id bigint NOT NULL,
    status character varying(20) DEFAULT 'in_corso'::character varying NOT NULL,
    owner_confirmed_at timestamp with time zone,
    offerer_confirmed_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_exchanges_completed CHECK ((((status)::text = 'completato'::text) = (completed_at IS NOT NULL))),
    CONSTRAINT ck_exchanges_status CHECK (((status)::text = ANY ((ARRAY['in_corso'::character varying, 'completato'::character varying, 'annullato'::character varying])::text[])))
);


--
-- Name: TABLE exchanges; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.exchanges IS 'Creato all''accettazione. Ancora la chat e le recensioni. Completato quando entrambi confermano.';


--
-- Name: exchanges_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.exchanges ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.exchanges_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: item_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_images (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    url character varying(500) NOT NULL,
    display_order smallint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_item_images_order CHECK (((display_order >= 0) AND (display_order <= 9))),
    CONSTRAINT ck_item_images_url_blank CHECK ((btrim((url)::text) <> ''::text))
);


--
-- Name: COLUMN item_images.url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.item_images.url IS 'Path/URL del file. I binari NON stanno nel DB.';


--
-- Name: item_images_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.item_images ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.item_images_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.items (
    id bigint NOT NULL,
    owner_id bigint NOT NULL,
    category_id bigint NOT NULL,
    title character varying(120) NOT NULL,
    description text NOT NULL,
    estimated_value numeric(10,2),
    item_condition character varying(20) NOT NULL,
    is_archived boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_items_condition CHECK (((item_condition)::text = ANY ((ARRAY['nuovo'::character varying, 'come_nuovo'::character varying, 'ottime'::character varying, 'buone'::character varying, 'discrete'::character varying, 'da_riparare'::character varying])::text[]))),
    CONSTRAINT ck_items_desc_not_blank CHECK ((btrim(description) <> ''::text)),
    CONSTRAINT ck_items_title_not_blank CHECK ((btrim((title)::text) <> ''::text)),
    CONSTRAINT ck_items_value_positive CHECK (((estimated_value IS NULL) OR (estimated_value >= (0)::numeric)))
);


--
-- Name: TABLE items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.items IS 'Oggetto posseduto da un utente. Puo'' esistere senza annuncio (offribile in una proposta).';


--
-- Name: COLUMN items.estimated_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.items.estimated_value IS 'Valore indicativo in EUR. NUMERIC, mai FLOAT.';


--
-- Name: items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.items ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: listing_accepted_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.listing_accepted_categories (
    listing_id bigint NOT NULL,
    category_id bigint NOT NULL
);


--
-- Name: TABLE listing_accepted_categories; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.listing_accepted_categories IS 'Nessuna riga per un annuncio = accetta qualsiasi categoria.';


--
-- Name: listings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.listings (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    city character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'attivo'::character varying NOT NULL,
    published_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_listings_city_not_blank CHECK ((btrim((city)::text) <> ''::text)),
    CONSTRAINT ck_listings_status CHECK (((status)::text = ANY ((ARRAY['attivo'::character varying, 'in_trattativa'::character varying, 'scambiato'::character varying, 'eliminato'::character varying])::text[])))
);


--
-- Name: TABLE listings; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.listings IS 'Pubblicazione di un item. Il proprietario si ricava da items.owner_id (no duplicazione).';


--
-- Name: listings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.listings ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.listings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.messages (
    id bigint NOT NULL,
    offer_id bigint CONSTRAINT messages_exchange_id_not_null NOT NULL,
    sender_id bigint NOT NULL,
    body text NOT NULL,
    sent_at timestamp with time zone DEFAULT now() NOT NULL,
    read_at timestamp with time zone,
    CONSTRAINT ck_messages_body_not_blank CHECK ((btrim(body) <> ''::text)),
    CONSTRAINT ck_messages_read_after_sent CHECK (((read_at IS NULL) OR (read_at >= sent_at)))
);


--
-- Name: COLUMN messages.read_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.messages.read_at IS 'NULL = non letto. Sostituisce is_read e in piu'' registra il quando.';


--
-- Name: messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.messages ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: offer_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offer_items (
    offer_id bigint NOT NULL,
    item_id bigint NOT NULL
);


--
-- Name: TABLE offer_items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.offer_items IS 'Invariante applicativa: item.owner_id = offer.offerer_id.';


--
-- Name: offers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offers (
    id bigint NOT NULL,
    listing_id bigint NOT NULL,
    offerer_id bigint NOT NULL,
    created_by_id bigint NOT NULL,
    parent_offer_id bigint,
    message text,
    status character varying(20) DEFAULT 'in_attesa'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    responded_at timestamp with time zone,
    CONSTRAINT ck_offers_not_self CHECK (((parent_offer_id IS NULL) OR (parent_offer_id <> id))),
    CONSTRAINT ck_offers_responded_at CHECK (((((status)::text = 'in_attesa'::text) AND (responded_at IS NULL)) OR (((status)::text <> 'in_attesa'::text) AND (responded_at IS NOT NULL)))),
    CONSTRAINT ck_offers_status CHECK (((status)::text = ANY ((ARRAY['in_attesa'::character varying, 'accettata'::character varying, 'rifiutata'::character varying, 'annullata'::character varying, 'controproposta'::character varying])::text[])))
);


--
-- Name: COLUMN offers.offerer_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.offers.offerer_id IS 'Controparte (proprietaria degli oggetti offerti). Costante su tutta la catena.';


--
-- Name: COLUMN offers.created_by_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.offers.created_by_id IS 'Autore di questo round: offerente, oppure proprietario in caso di controproposta.';


--
-- Name: offers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.offers ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.offers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports (
    id bigint NOT NULL,
    reporter_id bigint NOT NULL,
    reported_user_id bigint,
    reported_listing_id bigint,
    reason character varying(30) NOT NULL,
    description character varying(1000),
    status character varying(20) DEFAULT 'aperta'::character varying NOT NULL,
    reviewed_by_id bigint,
    reviewed_at timestamp with time zone,
    resolution_note character varying(1000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_reports_not_self CHECK (((reported_user_id IS NULL) OR (reported_user_id <> reporter_id))),
    CONSTRAINT ck_reports_reason CHECK (((reason)::text = ANY ((ARRAY['spam'::character varying, 'contenuto_offensivo'::character varying, 'truffa'::character varying, 'oggetto_illegale'::character varying, 'profilo_falso'::character varying, 'altro'::character varying])::text[]))),
    CONSTRAINT ck_reports_reviewed CHECK ((((status)::text = ANY ((ARRAY['aperta'::character varying, 'in_revisione'::character varying])::text[])) OR ((reviewed_by_id IS NOT NULL) AND (reviewed_at IS NOT NULL)))),
    CONSTRAINT ck_reports_single_target CHECK (((((reported_user_id IS NOT NULL))::integer + ((reported_listing_id IS NOT NULL))::integer) = 1)),
    CONSTRAINT ck_reports_status CHECK (((status)::text = ANY ((ARRAY['aperta'::character varying, 'in_revisione'::character varying, 'risolta'::character varying, 'respinta'::character varying])::text[])))
);


--
-- Name: reports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.reports ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reviews (
    id bigint NOT NULL,
    exchange_id bigint NOT NULL,
    author_id bigint NOT NULL,
    recipient_id bigint NOT NULL,
    rating smallint NOT NULL,
    comment character varying(1000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_reviews_not_self CHECK ((author_id <> recipient_id)),
    CONSTRAINT ck_reviews_rating CHECK (((rating >= 1) AND (rating <= 5)))
);


--
-- Name: reviews_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.reviews ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.reviews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: seq_app_users_user_id; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.seq_app_users_user_id
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: seq_app_users_user_id; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.seq_app_users_user_id OWNED BY public.app_users.user_id;


--
-- Name: user_reputation; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.user_reputation AS
 SELECT recipient_id AS user_id,
    count(*) AS reviews_count,
    round(avg(rating), 2) AS average_rating,
    min(rating) AS min_rating,
    max(rating) AS max_rating,
    max(created_at) AS last_review_at
   FROM public.reviews r
  GROUP BY recipient_id;


--
-- Name: VIEW user_reputation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON VIEW public.user_reputation IS 'Reputazione calcolata al volo. Utenti senza recensioni non compaiono: usare LEFT JOIN.';


--
-- Name: app_users app_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT app_users_pkey PRIMARY KEY (user_id);


--
-- Name: app_user_roles ck_app_user_roles_role; Type: CHECK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE public.app_user_roles
    ADD CONSTRAINT ck_app_user_roles_role CHECK (((role)::text = ANY (ARRAY[('GUEST'::character varying)::text, ('USER'::character varying)::text, ('ADMIN'::character varying)::text]))) NOT VALID;


--
-- Name: app_user_roles pk_app_user_roles; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_roles
    ADD CONSTRAINT pk_app_user_roles PRIMARY KEY (user_id, role);


--
-- Name: categories pk_categories; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT pk_categories PRIMARY KEY (id);


--
-- Name: exchanges pk_exchanges; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exchanges
    ADD CONSTRAINT pk_exchanges PRIMARY KEY (id);


--
-- Name: item_images pk_item_images; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_images
    ADD CONSTRAINT pk_item_images PRIMARY KEY (id);


--
-- Name: items pk_items; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT pk_items PRIMARY KEY (id);


--
-- Name: listing_accepted_categories pk_listing_accepted_categories; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listing_accepted_categories
    ADD CONSTRAINT pk_listing_accepted_categories PRIMARY KEY (listing_id, category_id);


--
-- Name: listings pk_listings; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT pk_listings PRIMARY KEY (id);


--
-- Name: messages pk_messages; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT pk_messages PRIMARY KEY (id);


--
-- Name: offer_items pk_offer_items; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_items
    ADD CONSTRAINT pk_offer_items PRIMARY KEY (offer_id, item_id);


--
-- Name: offers pk_offers; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT pk_offers PRIMARY KEY (id);


--
-- Name: reports pk_reports; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT pk_reports PRIMARY KEY (id);


--
-- Name: reviews pk_reviews; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT pk_reviews PRIMARY KEY (id);


--
-- Name: app_users uq_app_users_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT uq_app_users_email UNIQUE (email);


--
-- Name: app_users uq_app_users_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_users
    ADD CONSTRAINT uq_app_users_username UNIQUE (username);


--
-- Name: categories uq_categories_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT uq_categories_name UNIQUE (name);


--
-- Name: categories uq_categories_slug; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT uq_categories_slug UNIQUE (slug);


--
-- Name: exchanges uq_exchanges_offer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exchanges
    ADD CONSTRAINT uq_exchanges_offer UNIQUE (offer_id);


--
-- Name: item_images uq_item_images_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_images
    ADD CONSTRAINT uq_item_images_order UNIQUE (item_id, display_order);


--
-- Name: offers uq_offers_parent; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT uq_offers_parent UNIQUE (parent_offer_id);


--
-- Name: reviews uq_reviews_exchange_author; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT uq_reviews_exchange_author UNIQUE (exchange_id, author_id);


--
-- Name: idx_item_images_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_item_images_item ON public.item_images USING btree (item_id);


--
-- Name: idx_items_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_items_category ON public.items USING btree (category_id);


--
-- Name: idx_items_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_items_owner ON public.items USING btree (owner_id) WHERE (is_archived = false);


--
-- Name: idx_lac_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_lac_category ON public.listing_accepted_categories USING btree (category_id);


--
-- Name: idx_listings_active_published; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_listings_active_published ON public.listings USING btree (published_at DESC) WHERE ((status)::text = 'attivo'::text);


--
-- Name: idx_listings_city; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_listings_city ON public.listings USING btree (lower((city)::text)) WHERE ((status)::text = 'attivo'::text);


--
-- Name: idx_messages_offer_sent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_messages_offer_sent ON public.messages USING btree (offer_id, sent_at DESC);


--
-- Name: idx_messages_sender; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_messages_sender ON public.messages USING btree (sender_id);


--
-- Name: idx_messages_unread; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_messages_unread ON public.messages USING btree (offer_id) WHERE (read_at IS NULL);


--
-- Name: idx_offer_items_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offer_items_item ON public.offer_items USING btree (item_id);


--
-- Name: idx_offers_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_created_by ON public.offers USING btree (created_by_id);


--
-- Name: idx_offers_listing_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_listing_status ON public.offers USING btree (listing_id, status);


--
-- Name: idx_offers_offerer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_offerer ON public.offers USING btree (offerer_id, created_at DESC);


--
-- Name: idx_reports_open; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_open ON public.reports USING btree (status, created_at) WHERE ((status)::text = ANY ((ARRAY['aperta'::character varying, 'in_revisione'::character varying])::text[]));


--
-- Name: idx_reports_reported_listing; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_reported_listing ON public.reports USING btree (reported_listing_id);


--
-- Name: idx_reports_reported_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reports_reported_user ON public.reports USING btree (reported_user_id);


--
-- Name: idx_reviews_author; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_author ON public.reviews USING btree (author_id);


--
-- Name: idx_reviews_recipient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reviews_recipient ON public.reviews USING btree (recipient_id);


--
-- Name: uq_listings_active_item; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_listings_active_item ON public.listings USING btree (item_id) WHERE ((status)::text <> 'eliminato'::text);


--
-- Name: uq_offers_one_accepted; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_offers_one_accepted ON public.offers USING btree (listing_id) WHERE ((status)::text = 'accettata'::text);


--
-- Name: uq_offers_one_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_offers_one_pending ON public.offers USING btree (listing_id, offerer_id) WHERE ((status)::text = 'in_attesa'::text);


--
-- Name: uq_reports_open_listing; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reports_open_listing ON public.reports USING btree (reporter_id, reported_listing_id) WHERE ((reported_listing_id IS NOT NULL) AND ((status)::text = ANY ((ARRAY['aperta'::character varying, 'in_revisione'::character varying])::text[])));


--
-- Name: uq_reports_open_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reports_open_user ON public.reports USING btree (reporter_id, reported_user_id) WHERE ((reported_user_id IS NOT NULL) AND ((status)::text = ANY ((ARRAY['aperta'::character varying, 'in_revisione'::character varying])::text[])));


--
-- Name: app_user_roles fk_app_user_roles_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user_roles
    ADD CONSTRAINT fk_app_user_roles_user FOREIGN KEY (user_id) REFERENCES public.app_users(user_id) NOT VALID;


--
-- Name: exchanges fk_exchanges_offer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exchanges
    ADD CONSTRAINT fk_exchanges_offer FOREIGN KEY (offer_id) REFERENCES public.offers(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: item_images fk_item_images_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_images
    ADD CONSTRAINT fk_item_images_item FOREIGN KEY (item_id) REFERENCES public.items(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: items fk_items_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT fk_items_category FOREIGN KEY (category_id) REFERENCES public.categories(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: items fk_items_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.items
    ADD CONSTRAINT fk_items_owner FOREIGN KEY (owner_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: listing_accepted_categories fk_lac_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listing_accepted_categories
    ADD CONSTRAINT fk_lac_category FOREIGN KEY (category_id) REFERENCES public.categories(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: listing_accepted_categories fk_lac_listing; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listing_accepted_categories
    ADD CONSTRAINT fk_lac_listing FOREIGN KEY (listing_id) REFERENCES public.listings(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: listings fk_listings_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT fk_listings_item FOREIGN KEY (item_id) REFERENCES public.items(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: messages fk_messages_offer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fk_messages_offer FOREIGN KEY (offer_id) REFERENCES public.offers(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: messages fk_messages_sender; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.messages
    ADD CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: offer_items fk_offer_items_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_items
    ADD CONSTRAINT fk_offer_items_item FOREIGN KEY (item_id) REFERENCES public.items(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: offer_items fk_offer_items_offer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offer_items
    ADD CONSTRAINT fk_offer_items_offer FOREIGN KEY (offer_id) REFERENCES public.offers(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: offers fk_offers_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_created_by FOREIGN KEY (created_by_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: offers fk_offers_listing; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_listing FOREIGN KEY (listing_id) REFERENCES public.listings(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: offers fk_offers_offerer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_offerer FOREIGN KEY (offerer_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: offers fk_offers_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT fk_offers_parent FOREIGN KEY (parent_offer_id) REFERENCES public.offers(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: reports fk_reports_reported_listing; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk_reports_reported_listing FOREIGN KEY (reported_listing_id) REFERENCES public.listings(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: reports fk_reports_reported_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk_reports_reported_user FOREIGN KEY (reported_user_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: reports fk_reports_reporter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: reports fk_reports_reviewed_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT fk_reports_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: reviews fk_reviews_author; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_author FOREIGN KEY (author_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: reviews fk_reviews_exchange; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_exchange FOREIGN KEY (exchange_id) REFERENCES public.exchanges(id) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- Name: reviews fk_reviews_recipient; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT fk_reviews_recipient FOREIGN KEY (recipient_id) REFERENCES public.app_users(user_id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

