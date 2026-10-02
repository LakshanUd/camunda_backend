package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByIsActiveTrue();

    @Query("SELECT u FROM User u WHERE u.id IN :ids AND u.isActive = true")
    List<User> findActiveUsersByIds(@Param("ids") List<String> ids);
}
