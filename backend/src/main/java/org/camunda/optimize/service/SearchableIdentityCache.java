/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.lucene.search.SortField.STRING_LAST;

@Slf4j
public class SearchableIdentityCache implements AutoCloseable {
  private final Supplier<Long> maxEntryLimitSupplier;
  private final ByteBuffersDirectory memoryDirectory;
  private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

  private final AtomicLong entryCount = new AtomicLong(0);

  @SneakyThrows(IOException.class)
  public SearchableIdentityCache(final Supplier<Long> maxEntryLimitSupplier) {
    this.maxEntryLimitSupplier = maxEntryLimitSupplier;
    this.memoryDirectory = new ByteBuffersDirectory();
    // this is needed for the directory to be accessible by a reader in cases where no write has happened yet
    new IndexWriter(memoryDirectory, new IndexWriterConfig(getIndexAnalyzer())).close();
  }

  @Override
  public void close() {
    doWithWriteLock(() -> {
      try {
        this.memoryDirectory.close();
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed closing lucene in memory directory.", e);
      }
    });
  }

  public void addIdentity(@NonNull final IdentityWithMetadataResponseDto identity) {
    doWithWriteLock(() -> {
      enforceMaxEntryLimit(1);
      try (final IndexWriter indexWriter =
             new IndexWriter(memoryDirectory, new IndexWriterConfig(getIndexAnalyzer()))) {
        writeIdentityDto(indexWriter, identity);
        entryCount.incrementAndGet();
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed writing identity [id:" + identity.getId() + "].", e);
      }
    });
  }

  public void addIdentities(@NonNull final List<? extends IdentityWithMetadataResponseDto> identities) {
    if (identities.isEmpty()) {
      return;
    }

    doWithWriteLock(() -> {
      enforceMaxEntryLimit(identities.size());
      try (final IndexWriter indexWriter = new IndexWriter(
        memoryDirectory,
        new IndexWriterConfig(getIndexAnalyzer())
      )) {
        identities.forEach(identity -> writeIdentityDto(indexWriter, identity));
        entryCount.addAndGet(identities.size());
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed writing identities.", e);
      }
    });
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityByIdAndType(final String id, final IdentityType type) {
    return getTypedIdentityDtoById(id, type, SearchableIdentityCache::mapDocumentToIdentityDto);
  }

  public Optional<UserDto> getUserIdentityById(final String id) {
    return getTypedIdentityDtoById(id, IdentityType.USER, SearchableIdentityCache::mapDocumentToUserDto);
  }

  public Optional<GroupDto> getGroupIdentityById(final String id) {
    return getTypedIdentityDtoById(id, IdentityType.GROUP, SearchableIdentityCache::mapDocumentToGroupDto);
  }

  public List<IdentityWithMetadataResponseDto> getIdentities(final Collection<IdentityDto> identities) {
    final List<IdentityWithMetadataResponseDto> result = new ArrayList<>(identities.size());
    doWithReadLock(() -> {
      try (final IndexReader indexReader = DirectoryReader.open(memoryDirectory)) {
        final IndexSearcher searcher = new IndexSearcher(indexReader);

        final BooleanQuery.Builder searchBuilder = new BooleanQuery.Builder();
        identities.forEach(identity -> {
          final BooleanQuery.Builder entryFilter = new BooleanQuery.Builder();
          entryFilter.add(
            new TermQuery(new Term(IdentityDto.Fields.id, identity.getId())),
            BooleanClause.Occur.MUST
          );
          entryFilter.add(
            new TermQuery(new Term(IdentityDto.Fields.type, identity.getType().name())),
            BooleanClause.Occur.MUST
          );
          searchBuilder.add(entryFilter.build(), BooleanClause.Occur.SHOULD);
        });

        final TopDocs topDocs = searcher.search(searchBuilder.build(), identities.size());
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
          final Document document = searcher.doc(scoreDoc.doc);
          final IdentityWithMetadataResponseDto identityRestDto = mapDocumentToIdentityDto(document);
          result.add(identityRestDto);
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed searching for identities by id.", e);
      }
    });
    return result;
  }

  public List<UserDto> getUsersByEmail(final List<String> emails) {
    List<UserDto> users = new ArrayList<>();
    doWithReadLock(() -> {
      try (final IndexReader indexReader = DirectoryReader.open(memoryDirectory)) {
        final IndexSearcher searcher = new IndexSearcher(indexReader);
        final BooleanQuery.Builder searchBuilder = new BooleanQuery.Builder();
        final BooleanQuery.Builder entryFilter = new BooleanQuery.Builder();
        final List<BytesRef> emailByteRefs = emails.stream()
          .map(BytesRef::new)
          .collect(Collectors.toList());
        entryFilter.add(
          new TermInSetQuery(UserDto.Fields.email, emailByteRefs),
          BooleanClause.Occur.MUST
        );
        entryFilter.add(
          new TermQuery(new Term(IdentityDto.Fields.type, IdentityType.USER.name())),
          BooleanClause.Occur.MUST
        );
        searchBuilder.add(entryFilter.build(), BooleanClause.Occur.SHOULD);
        final TopDocs topDocs = searcher.search(searchBuilder.build(), emails.size());
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
          final Document document = searcher.doc(scoreDoc.doc);
          final UserDto userDto = mapDocumentToUserDto(document);
          users.add(userDto);
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed searching for users by email.", e);
      }
    });
    return users;
  }

  public IdentitySearchResultResponseDto searchIdentities(final String terms) {
    return searchIdentities(terms, 10);
  }

  public IdentitySearchResultResponseDto searchIdentities(final String terms, final int resultLimit) {
    return searchIdentities(terms, IdentityType.values(), resultLimit);
  }

  public IdentitySearchResultResponseDto searchIdentities(final String terms,
                                                          final IdentityType[] identityTypes,
                                                          final int resultLimit) {
    return searchIdentitiesAfter(terms, identityTypes, resultLimit, null);
  }

  public IdentitySearchResultResponseDto searchIdentitiesAfter(final String terms,
                                                               final int resultLimit,
                                                               final IdentitySearchResultResponseDto searchAfter) {
    return searchIdentitiesAfter(terms, IdentityType.values(), resultLimit, searchAfter);
  }

  public IdentitySearchResultResponseDto searchIdentitiesAfter(final String terms,
                                                               final IdentityType[] identityTypes,
                                                               final int resultLimit,
                                                               final IdentitySearchResultResponseDto searchAfter) {
    return searchIdentitiesAfter(terms, null, identityTypes, resultLimit, searchAfter);
  }

  public IdentitySearchResultResponseDto searchAmongIdentitiesWithIds(final String terms,
                                                                      final Collection<String> identityIds,
                                                                      final IdentityType[] identityTypes,
                                                                      final int resultLimit) {
    return searchIdentitiesAfter(
      terms,
      new TermInSetQuery(IdentityDto.Fields.id, identityIds.stream().map(BytesRef::new).collect(Collectors.toSet())),
      identityTypes,
      resultLimit,
      null
    );
  }

  public long getSize() {
    return entryCount.get();
  }

  @VisibleForTesting
  public long getCacheSizeInBytes() {
    AtomicLong size = new AtomicLong();
    doWithReadLock(() -> {
      try {
        size.set(
          Arrays.stream(memoryDirectory.listAll())
            .map(file -> {
              try {
                return memoryDirectory.fileLength(file);
              } catch (IOException e) {
                log.error("Failed reading file from in memory directory.", e);
                return 0L;
              }
            })
            .reduce(Long::sum)
            .orElse(0L)
        );
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed getting size of lucene directory.", e);
      }
    });
    return size.get();
  }

  private IdentitySearchResultResponseDto searchIdentitiesAfter(final String terms,
                                                                final Query additionalFilterQuery,
                                                                final IdentityType[] identityTypes,
                                                                final int resultLimit,
                                                                final IdentitySearchResultResponseDto searchAfter) {
    final IdentitySearchResultResponseDto result = new IdentitySearchResultResponseDto();
    doWithReadLock(() -> {
      try (final IndexReader indexReader = DirectoryReader.open(memoryDirectory)) {
        final IndexSearcher searcher = new IndexSearcher(indexReader);

        final BooleanQuery.Builder termsAndIdFilterQuery = new BooleanQuery.Builder();
        if (additionalFilterQuery != null) {
          termsAndIdFilterQuery.add(additionalFilterQuery, BooleanClause.Occur.FILTER);
        }
        termsAndIdFilterQuery.add(new BooleanClause(
          createSearchIdentityQuery(terms, identityTypes), BooleanClause.Occur.MUST
        ));

        final TopDocs topDocs = searcher.searchAfter(
          Optional.ofNullable(searchAfter).map(IdentitySearchResultResponseDto::getScoreDoc).orElse(null),
          termsAndIdFilterQuery.build(), resultLimit, createNameSorting()
        );

        result.setTotal(topDocs.totalHits.value);
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
          final Document document = searcher.doc(scoreDoc.doc);
          final IdentityWithMetadataResponseDto identityRestDto = mapDocumentToIdentityDto(document);
          result.getResult().add(identityRestDto);
          result.setScoreDoc(scoreDoc);
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed searching for identities with terms [id:" + terms + "].", e);
      }
    });
    return result;
  }

  private Sort createNameSorting() {
    final SortField nameSort = new SortField(IdentityWithMetadataResponseDto.Fields.name, SortField.Type.STRING, false);
    nameSort.setMissingValue(STRING_LAST);
    return new Sort(SortField.FIELD_SCORE, nameSort);
  }

  private void enforceMaxEntryLimit(int newRecordCount) {
    if (entryCount.get() + newRecordCount > maxEntryLimitSupplier.get()) {
      throw new MaxEntryLimitHitException();
    }
  }

  private void doWithReadLock(final Runnable readOperation) {
    readWriteLock.readLock().lock();
    try {
      readOperation.run();
    } finally {
      readWriteLock.readLock().unlock();
    }
  }

  private void doWithWriteLock(final Runnable writeOperation) {
    readWriteLock.writeLock().lock();
    try {
      writeOperation.run();
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }

  private <T extends IdentityWithMetadataResponseDto> Optional<T> getTypedIdentityDtoById(
    final String id,
    final IdentityType identityType,
    final Function<Document, T> mapperFunction) {
    AtomicReference<T> result = new AtomicReference<>();
    doWithReadLock(() -> {
      try (final IndexReader indexReader = DirectoryReader.open(memoryDirectory)) {
        final IndexSearcher searcher = new IndexSearcher(indexReader);
        final BooleanQuery.Builder searchBuilder = new BooleanQuery.Builder();
        searchBuilder.add(
          new TermQuery(new Term(IdentityDto.Fields.id, id)),
          BooleanClause.Occur.MUST
        );
        searchBuilder.add(
          new TermQuery(new Term(IdentityDto.Fields.type, identityType.name())), BooleanClause.Occur.MUST
        );
        final TopDocs topDocs = searcher.search(searchBuilder.build(), 1);
        if (topDocs.totalHits.value > 0) {
          result.set(mapperFunction.apply(searcher.doc(topDocs.scoreDocs[0].doc)));
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed getting identity by [id:" + id + "].", e);
      }
    });
    return Optional.ofNullable(result.get());
  }

  @SneakyThrows
  private void writeIdentityDto(final IndexWriter indexWriter, final IdentityWithMetadataResponseDto identity) {
    indexWriter.updateDocument(
      new Term(IdentityDto.Fields.id, identity.getId()), mapIdentityDtoToDocument(identity)
    );
  }

  private BooleanQuery createSearchIdentityQuery(final String searchQuery, final IdentityType[] identityTypes) {
    final List<String> searchTerms = tokenizeSearchQuery(searchQuery);

    final String[] termsArray = searchTerms.toArray(new String[]{});
    final BooleanQuery.Builder searchBuilder = new BooleanQuery.Builder();

    searchBuilder.add(
      new TermInSetQuery(
        IdentityDto.Fields.type,
        Arrays.stream(identityTypes).map(IdentityType::name).map(BytesRef::new).collect(Collectors.toSet())
      ),
      BooleanClause.Occur.MUST
    );

    if (StringUtils.isNotEmpty(searchQuery)) {
      searchBuilder.setMinimumNumberShouldMatch(1);

      // explicit to lowercase field ofr id for exact match ignoring case
      final String allLowerCaseIdField = getAllLowerCaseFieldForDtoField(IdentityDto.Fields.id);
      searchBuilder.add(
        new PrefixQuery(new Term(allLowerCaseIdField, searchQuery.toLowerCase())),
        BooleanClause.Occur.SHOULD
      );
      // exact id matches are boosted
      searchBuilder.add(
        // as there are 2 other field clauses and this one should win even if all the others match
        // as one other field has boost of 3, boost by 4 + 2 to let this matcher always win on scoring
        // explicit to lowercase as we also do to lowercase on insert, for cheap case insensitivity
        new BoostQuery(new TermQuery(new Term(allLowerCaseIdField, searchQuery.toLowerCase())), 6),
        BooleanClause.Occur.SHOULD
      );

      // do phrase query on name as it may consist of multiple terms and phrase matches are to be preferred
      // boost it by 3 as it is more important than a prefix email and prefix id match
      searchBuilder.add(
        new BoostQuery(
          new PhraseQuery(getNgramFieldForDtoField(IdentityWithMetadataResponseDto.Fields.name), termsArray),
          3
        ),
        BooleanClause.Occur.SHOULD
      );

      searchBuilder.add(
        // explicit to lowercase as we also do to lowercase on insert, for cheap case insensitivity
        new PrefixQuery(
          new Term(getAllLowerCaseFieldForDtoField(UserDto.Fields.email), searchQuery.toLowerCase())
        ),
        BooleanClause.Occur.SHOULD
      );
    } else {
      searchBuilder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
    }
    return searchBuilder.build();
  }

  @SneakyThrows(IOException.class)
  private static List<String> tokenizeSearchQuery(final String searchTerms) {
    final List<String> tokens = new ArrayList<>();
    try (TokenStream tokenStream = getSearchTermAnalyzer().tokenStream(null, new StringReader(searchTerms))) {
      tokenStream.reset();
      while (tokenStream.incrementToken()) {
        tokens.add(tokenStream.getAttribute(CharTermAttribute.class).toString());
      }
    }
    return tokens;
  }

  @SneakyThrows(IOException.class)
  private static Analyzer getIndexAnalyzer() {
    return CustomAnalyzer.builder()
      .withTokenizer("whitespace")
      .addTokenFilter("lowercase")
      .addTokenFilter("ngram", "minGramSize", "1", "maxGramSize", "16")
      .build();
  }

  @SneakyThrows(IOException.class)
  private static Analyzer getSearchTermAnalyzer() {
    return CustomAnalyzer.builder()
      .withTokenizer("whitespace")
      .addTokenFilter("lowercase")
      .build();
  }

  private static Document mapIdentityDtoToDocument(final IdentityWithMetadataResponseDto identity) {
    final Document document = new Document();

    document.add(new StringField(IdentityDto.Fields.id, identity.getId(), Field.Store.YES));
    // this all lowercase id field is used for cheap case insensitive full term/prefix search
    document.add(new StringField(
      getAllLowerCaseFieldForDtoField(IdentityDto.Fields.id), identity.getId().toLowerCase(), Field.Store.NO)
    );
    document.add(new StringField(IdentityDto.Fields.type, identity.getType().name(), Field.Store.YES));
    Optional.ofNullable(identity.getName()).ifPresent(name -> {
      // as we want to use custom sorting based on name we need to store the name value as sorted doc field
      document.add(new SortedDocValuesField(
        IdentityWithMetadataResponseDto.Fields.name,
        new BytesRef(name.toLowerCase())
      ));
      document.add(new StringField(IdentityWithMetadataResponseDto.Fields.name, name, Field.Store.YES));
      document.add(new TextField(
        getNgramFieldForDtoField(IdentityWithMetadataResponseDto.Fields.name),
        name.toLowerCase(),
        Field.Store.YES
      ));
    });

    if (identity instanceof UserDto) {
      final UserDto userDto = (UserDto) identity;
      Optional.ofNullable(userDto.getFirstName()).ifPresent(
        firstName -> document.add(new StringField(UserDto.Fields.firstName, firstName, Field.Store.YES))
      );
      Optional.ofNullable(userDto.getLastName()).ifPresent(
        lastName -> document.add(new StringField(UserDto.Fields.lastName, lastName, Field.Store.YES))
      );
      Optional.ofNullable(userDto.getEmail()).ifPresent(email -> {
        document.add(new StringField(UserDto.Fields.email, email, Field.Store.YES));
        // this all lowercase id field is used for cheap case insensitive full term/prefix search
        document.add(new StringField(
          getAllLowerCaseFieldForDtoField(UserDto.Fields.email), email.toLowerCase(), Field.Store.NO)
        );
      });
      Optional.ofNullable(userDto.getRoles()).stream().flatMap(Collection::stream).forEach(
        role -> document.add(new StringField(UserDto.Fields.roles, role, Field.Store.YES))
      );
    } else if (identity instanceof GroupDto) {
      final GroupDto groupDto = (GroupDto) identity;
      Optional.ofNullable(groupDto.getMemberCount()).ifPresent(
        memberCount -> document.add(new StoredField(GroupDto.Fields.memberCount, memberCount))
      );
    }
    return document;
  }

  private static String getAllLowerCaseFieldForDtoField(final String fieldName) {
    return fieldName + ".allLowerCase";
  }

  private static String getNgramFieldForDtoField(final String fieldName) {
    return fieldName + ".ngram";
  }

  private static IdentityWithMetadataResponseDto mapDocumentToIdentityDto(final Document document) {
    final IdentityType identityType = IdentityType.valueOf(document.get(IdentityDto.Fields.type));
    switch (identityType) {
      case USER:
        return mapDocumentToUserDto(document);
      case GROUP:
        return mapDocumentToGroupDto(document);
      default:
        throw new OptimizeRuntimeException("Unsupported identity type :" + identityType.name() + ".");
    }
  }

  private static GroupDto mapDocumentToGroupDto(final Document document) {
    return new GroupDto(
      document.get(IdentityDto.Fields.id),
      document.get(IdentityWithMetadataResponseDto.Fields.name),
      Optional.ofNullable(document.get(GroupDto.Fields.memberCount)).map(Long::valueOf).orElse(null)
    );
  }

  private static UserDto mapDocumentToUserDto(final Document document) {
    return new UserDto(
      document.get(IdentityDto.Fields.id),
      document.get(UserDto.Fields.firstName),
      document.get(UserDto.Fields.lastName),
      document.get(UserDto.Fields.email),
      Arrays.asList(document.getValues(UserDto.Fields.roles))
    );
  }

}
