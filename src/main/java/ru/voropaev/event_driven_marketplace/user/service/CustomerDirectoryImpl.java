package ru.voropaev.event_driven_marketplace.user.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.voropaev.event_driven_marketplace.user.domain.User;
import ru.voropaev.event_driven_marketplace.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerDirectoryImpl implements CustomerDirectory {
    private final UserRepository userRepository;

    public CustomerDirectoryImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> emailOf(UUID customerId) {
        return userRepository.findById(customerId)
                .map(User::getEmail);
    }
}
