/*
 * Copyright 2011-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.repository.support;

import org.springframework.data.neo4j.core.mapping.Neo4jPersistentProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apiguardian.api.API;
import org.neo4j.cypherdsl.core.Statement;
import org.reactivestreams.Publisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.neo4j.core.ReactiveNeo4jOperations;
import org.springframework.data.neo4j.core.mapping.Neo4jPersistentEntity;
import org.springframework.data.neo4j.core.mapping.CypherGenerator;
import org.springframework.data.neo4j.repository.query.CypherAdapterUtils;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Repository base implementation for Neo4j.
 *
 * @author Gerrit Meier
 * @author Michael J. Simons
 * @since 6.0
 * @param <T> the type of the domain class managed by this repository
 * @param <ID> the type of the unique identifier of the domain class
 */
@Repository
@Transactional(readOnly = true)
@API(status = API.Status.STABLE, since = "6.0")
public class SimpleReactiveNeo4jRepository<T, ID> implements ReactiveSortingRepository<T, ID> {

	private final ReactiveNeo4jOperations neo4jOperations;

	private final Neo4jEntityInformation<T, ID> entityInformation;

	private final Neo4jPersistentEntity<T> entityMetaData;

	private final CypherGenerator cypherGenerator;

	protected SimpleReactiveNeo4jRepository(ReactiveNeo4jOperations neo4jOperations,
			Neo4jEntityInformation<T, ID> entityInformation) {

		this.neo4jOperations = neo4jOperations;
		this.entityInformation = entityInformation;
		this.entityMetaData = this.entityInformation.getEntityMetaData();
		this.cypherGenerator = CypherGenerator.INSTANCE;
	}

	@Override
	public Mono<T> findById(ID id) {

		return neo4jOperations.findById(id, this.entityInformation.getJavaType());
	}

	@Override
	public Mono<T> findById(Publisher<ID> idPublisher) {
		return Mono.from(idPublisher).flatMap(this::findById);
	}

	@Override
	public Flux<T> findAllById(Iterable<ID> ids) {

		return this.neo4jOperations.findAllById(ids, this.entityInformation.getJavaType());
	}

	@Override
	public Flux<T> findAllById(Publisher<ID> idStream) {
		return Flux.from(idStream).buffer().flatMap(this::findAllById);
	}

	@Override
	public Flux<T> findAll() {

		return this.neo4jOperations.findAll(this.entityInformation.getJavaType());
	}

	@Override
	public Flux<T> findAll(Sort sort) {
		Statement statement = cypherGenerator.prepareMatchOf(entityMetaData)
				.returning(cypherGenerator.createReturnStatementForMatch(entityMetaData))
				.orderBy(CypherAdapterUtils.toSortItems(entityMetaData, sort)).build();

		return neo4jOperations.findAll(statement, this.entityInformation.getJavaType());
	}

	@Override
	public Mono<Long> count() {

		return this.neo4jOperations.count(this.entityInformation.getJavaType());
	}

	@Override
	public Mono<Boolean> existsById(ID id) {
		return findById(id).hasElement();
	}

	@Override
	public Mono<Boolean> existsById(Publisher<ID> idPublisher) {
		return Mono.from(idPublisher).flatMap(this::existsById);
	}

	@Override
	@Transactional
	public <S extends T> Mono<S> save(S entity) {

		return this.neo4jOperations.save(entity);
	}

	@Override
	@Transactional
	public <S extends T> Flux<S> saveAll(Iterable<S> entities) {

		return this.neo4jOperations.saveAll(entities);
	}

	@Override
	@Transactional
	public <S extends T> Flux<S> saveAll(Publisher<S> entityStream) {

		return Flux.from(entityStream).flatMap(this::save);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#deleteById(java.lang.Object)
	 */
	@Override
	@Transactional
	public Mono<Void> deleteById(ID id) {

		return this.neo4jOperations.deleteById(id, this.entityInformation.getJavaType());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#deleteById(org.reactivestreams.Publisher)
	 */
	@Override
	@Transactional
	public Mono<Void> deleteById(Publisher<ID> idPublisher) {

		Assert.notNull(idPublisher, "The given Publisher of an id must not be null!");
		return Mono.from(idPublisher).flatMap(this::deleteById);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#delete(java.lang.Object)
	 */
	@Override
	@Transactional
	public Mono<Void> delete(T entity) {
		Assert.notNull(entity, "The given entity must not be null!");

		ID id = this.entityInformation.getId(entity);
		if (entityMetaData.hasVersionProperty()) {
			Neo4jPersistentProperty versionProperty = entityMetaData.getRequiredVersionProperty();
			Object versionValue = entityMetaData.getPropertyAccessor(entity).getProperty(versionProperty);
			return this.neo4jOperations.deleteByIdWithVersion(id, this.entityInformation.getJavaType(), versionProperty, versionValue);
		} else {
			return this.deleteById(id);
		}

	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#deleteAll()
	 */
	@Override
	@Transactional
	public Mono<Void> deleteAll() {

		return this.neo4jOperations.deleteAll(this.entityInformation.getJavaType());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#deleteAll(java.lang.Iterable)
	 */
	@Override
	@Transactional
	public Mono<Void> deleteAll(Iterable<? extends T> entities) {

		Assert.notNull(entities, "The given Iterable of entities must not be null!");
		List<ID> ids = StreamSupport.stream(entities.spliterator(), false).map(this.entityInformation::getId)
				.collect(Collectors.toList());
		return this.neo4jOperations.deleteAllById(ids, this.entityInformation.getJavaType());
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.reactive.ReactiveCrudRepository#deleteAll(org.reactivestreams.Publisher)
	 */
	@Override
	@Transactional
	public Mono<Void> deleteAll(Publisher<? extends T> entitiesPublisher) {

		Assert.notNull(entitiesPublisher, "The given Publisher of entities must not be null!");
		return Flux.from(entitiesPublisher).flatMap(this::delete).then();
	}
}