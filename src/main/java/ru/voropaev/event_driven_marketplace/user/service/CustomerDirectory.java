package ru.voropaev.event_driven_marketplace.user.service;

import java.util.Optional;
import java.util.UUID;

public interface CustomerDirectory {
    Optional<String> emailOf(UUID customerId);
}
