package com.securedoc.securedoc_ai.controller;

import com.securedoc.securedoc_ai.dto.UserResponse;
import com.securedoc.securedoc_ai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }
}