package com.example.camunda_backend.repository;

import com.example.camunda_backend.entity.CustomProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomProcessInstanceRepository extends JpaRepository<CustomProcessInstance, String> {

    List<CustomProcessInstance> findByStatusOrderByStartTimeDesc(String status);

    List<CustomProcessInstance> findByProcessDefinitionKeyOrderByStartTimeDesc(String processDefinitionKey);

    List<CustomProcessInstance> findByStartUserIdOrderByStartTimeDesc(String startUserId);

    List<CustomProcessInstance> findAllByOrderByStartTimeDesc();
}
