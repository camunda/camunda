package io.camunda.jokegen.service;

import io.camunda.jokegen.repository.AppUserRepository;
import io.camunda.jokegen.repository.UserRoleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Profile("basic")
public class AppUserDetailsService implements UserDetailsService {

  private final AppUserRepository appUserRepository;
  private final UserRoleRepository userRoleRepository;

  public AppUserDetailsService(
      final AppUserRepository appUserRepository, final UserRoleRepository userRoleRepository) {
    this.appUserRepository = appUserRepository;
    this.userRoleRepository = userRoleRepository;
  }

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    final var appUser =
        appUserRepository
            .findByUsername(username)
            .orElseThrow(
                () -> new UsernameNotFoundException("User not found: " + username));

    final var roles = userRoleRepository.findRoleNamesByUsername(username);
    final var authorities =
        roles.stream().map(SimpleGrantedAuthority::new).toList();

    return new User(appUser.getUsername(), appUser.getPassword(), authorities);
  }
}
