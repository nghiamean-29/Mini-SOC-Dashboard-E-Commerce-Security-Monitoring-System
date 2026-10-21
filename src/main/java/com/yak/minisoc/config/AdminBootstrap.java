
package com.yak.minisoc.config;

import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        String username = System.getenv("ADMIN_USERNAME");
        String email = System.getenv("ADMIN_EMAIL");
        String password = System.getenv("ADMIN_PASSWORD");

        // Không có cấu hình bootstrap thì không tạo tài khoản.
        if (username == null && email == null && password == null) {
            return;
        }

        if (username == null || email == null || password == null
            || username.isBlank() || email.isBlank()
            || password.length() < 12) {
            throw new IllegalStateException(
                "Cấu hình ADMIN chưa đầy đủ hoặc mật khẩu quá ngắn."
            );
        }

        if (userRepository.existsByUsername(username)
            || userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                "Username hoặc email ADMIN đã tồn tại. "
                    + "Hãy xóa cấu hình bootstrap sau khi tạo tài khoản."
            );
        }

        Role adminRole = roleRepository.findByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy role ADMIN trong database."
            ));

        User admin = User.create(
            username,
            email,
            passwordEncoder.encode(password),
            "Mini SOC Administrator"
        );

        admin.addRole(adminRole);
        userRepository.save(admin);

        System.out.println(
            "Da tao tai khoan ADMIN: " + username
        );
    }
}
