package io.camunda.jokegen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "jokes")
public class Joke {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String setup;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String punchline;

  @Column(nullable = false, length = 50)
  private String category;

  @Column(name = "created_by", nullable = false, length = 100)
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  protected Joke() {}

  public Joke(final String setup, final String punchline, final String category,
      final String createdBy) {
    this.setup = setup;
    this.punchline = punchline;
    this.category = category;
    this.createdBy = createdBy;
    this.createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(final Long id) {
    this.id = id;
  }

  public String getSetup() {
    return setup;
  }

  public void setSetup(final String setup) {
    this.setup = setup;
  }

  public String getPunchline() {
    return punchline;
  }

  public void setPunchline(final String punchline) {
    this.punchline = punchline;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(final String category) {
    this.category = category;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(final String createdBy) {
    this.createdBy = createdBy;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(final LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
