package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.BusinessDataField;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BusinessDataRepository extends JpaRepository<BusinessDataField, Long> {
    
    // Fetches all business data fields attached to a workflow instance (for UI rendering)
    List<BusinessDataField> findByProcessInstanceId(String processInstanceId);

    // Finds a specific field by process ID and key to update existing records instead of duplicating them
    Optional<BusinessDataField> findByProcessInstanceIdAndFieldKey(String processInstanceId, String fieldKey);
}