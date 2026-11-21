package com.example.navigation

object MemoDeckRoutes {
    // 4 Bottom Navigation Tabs
    const val ROUTE_HOME = "home"
    const val ROUTE_DECKS = "decks"
    const val ROUTE_STUDY_TAB = "study_tab"
    const val ROUTE_QUIZ_TAB = "quiz_tab"

    // Deck & Flashcard Management
    const val ROUTE_CREATE_DECK = "create_deck"
    const val ROUTE_DECK_DETAILS = "deck_details/{deckId}"
    const val ROUTE_CARD_LIST = "card_list/{deckId}"
    const val ROUTE_CREATE_CARD = "create_card/{deckId}"
    const val ROUTE_EDIT_CARD = "edit_card/{deckId}/{cardId}"
    const val ROUTE_CARD_VIEWER = "card_viewer/{cardId}"

    // Study & Quiz Sessions
    const val ROUTE_STUDY_DECK = "study_deck/{deckId}?startIndex={startIndex}"
    const val ROUTE_STUDY_WEAK = "study_weak"
    const val ROUTE_QUIZ_DECK = "quiz_deck/{deckId}"

    // Home Tools & Settings
    const val ROUTE_WEAK_CARDS = "weak_cards"
    const val ROUTE_SEARCH = "search"
    const val ROUTE_STATISTICS = "statistics"
    const val ROUTE_SETTINGS = "settings"

    // Automatic / Bulk Card Creation Modules
    const val ROUTE_IMAGE_IMPORT = "image_import/{deckId}"
    const val ROUTE_FILE_IMPORT = "file_import/{deckId}"
    const val ROUTE_PDF_IMPORT = "pdf_import/{deckId}"

    fun deckDetails(deckId: Long) = "deck_details/$deckId"
    fun cardList(deckId: Long) = "card_list/$deckId"
    fun createCard(deckId: Long) = "create_card/$deckId"
    fun editCard(deckId: Long, cardId: Long) = "edit_card/$deckId/$cardId"
    fun cardViewer(cardId: Long) = "card_viewer/$cardId"
    fun studyDeck(deckId: Long, startIndex: Int = 0) = "study_deck/$deckId?startIndex=$startIndex"
    fun quizDeck(deckId: Long) = "quiz_deck/$deckId"
    fun imageImport(deckId: Long) = "image_import/$deckId"
    fun fileImport(deckId: Long) = "file_import/$deckId"
    fun pdfImport(deckId: Long) = "pdf_import/$deckId"

    val topLevelTabs = setOf(ROUTE_HOME, ROUTE_DECKS, ROUTE_STUDY_TAB, ROUTE_QUIZ_TAB)
}
