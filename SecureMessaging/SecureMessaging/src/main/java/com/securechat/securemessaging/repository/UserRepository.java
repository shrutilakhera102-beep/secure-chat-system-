package com.securechat.securemessaging.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.securechat.securemessaging.model.User;

public interface UserRepository extends JpaRepository<User, Integer> {

    User findByUsername(String username);
}
