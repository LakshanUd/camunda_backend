package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.AppGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppGroupRepository extends JpaRepository<AppGroup, Long> {

    Optional<AppGroup> findByName(String name);

    boolean existsByName(String name);
}
