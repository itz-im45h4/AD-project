package com.ridelink.account.repository;

import com.ridelink.account.domain.User;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for Account User documents.
 *
 * <p>Enforces case-insensitive email indexing and lookup methods to prevent duplicate
 * registrations caused by casing variations.</p>
 */
public interface UserRepository extends MongoRepository<User, String> {

    /** Case-insensitive lookup for user credentials authentication. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Verifies uniqueness of email address during registration. */
    boolean existsByEmailIgnoreCase(String email);
}
