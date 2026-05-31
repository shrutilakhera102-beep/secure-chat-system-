package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByName(String name);

    boolean existsByName(String name);

    /** All groups that a given user belongs to. */
    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.username = :username")
    List<Group> findGroupsByUsername(@Param("username") String username);
}
