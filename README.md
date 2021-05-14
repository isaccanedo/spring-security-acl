## Spring Boot Security ACL

# 1. Introdução
Lista de controle de acesso (ACL) é uma lista de permissões anexadas a um objeto. Uma ACL especifica quais identidades são concedidas a quais operações em um determinado objeto.

A lista de controle de acesso Spring Security é um componente Spring que oferece suporte à segurança de objeto de domínio. Simplificando, Spring ACL ajuda a definir permissões para usuário /função específica em um único objeto de domínio - em vez de em toda a linha, no nível típico por operação.

Por exemplo, um usuário com a função Admin pode ver (LER) e editar (ESCREVER) todas as mensagens em uma Caixa Central de Notificação, mas o usuário normal apenas pode ver as mensagens, relacionar-se com elas e não pode editar. Enquanto isso, outros usuários com a função Editor podem ver e editar algumas mensagens específicas.

Conseqüentemente, diferentes usuários /funções têm diferentes permissões para cada objeto específico. Nesse caso, o Spring ACL é capaz de realizar a tarefa. Exploraremos como configurar a verificação de permissão básica com Spring ACL neste artigo.

# 2. Configuração
### 2.1. Banco de dados ACL
Para usar Spring Security ACL, precisamos criar quatro tabelas obrigatórias em nosso banco de dados.

A primeira tabela é ACL_CLASS, que armazena o nome da classe do objeto de domínio, as colunas incluem:

- ID;
- CLASSE: o nome da classe de objetos de domínio protegidos, por exemplo: com.isaccanedo.acl.persistence.entity.NoticeMessage
Em segundo lugar, precisamos da tabela ACL_SID, que nos permite identificar universalmente qualquer princípio ou autoridade no sistema. A mesa precisa de:

- ID;
- SID: qual é o nome de usuário ou nome da função. SID significa Security Identity;
- PRINCIPAL: 0 ou 1, para indicar que o SID correspondente é um principal (usuário, como mary, mike, jack ...) ou uma autoridade (papel, como ROLE_ADMIN, ROLE_USER, ROLE_EDITOR ...)
A próxima tabela é ACL_OBJECT_IDENTITY, que armazena informações para cada objeto de domínio exclusivo:

- ID;
- OBJECT_ID_CLASS: define a classe do objeto de domínio, links para a tabela ACL_CLASS;
- OBJECT_ID_IDENTITY: objetos de domínio podem ser armazenados em várias tabelas dependendo da classe. Portanto, este campo armazena a chave primária do objeto de destino
- PARENT_OBJECT: especifica o pai desta Identidade de Objeto nesta tabela;
- OWNER_SID: ID do proprietário do objeto, links para a tabela ACL_SID
ENTRIES_INHERITTING: se as entradas ACL deste objeto são herdadas do objeto pai (as entradas ACL são definidas na tabela ACL_ENTRY)
Finalmente, a permissão individual de armazenamento ACL_ENTRY atribuída a cada SID em uma identidade de objeto:

- ID;
- ACL_OBJECT_IDENTITY: especifica a identidade do objeto, links para a tabela ACL_OBJECT_IDENTITY;
- ACE_ORDER: a ordem da entrada atual na lista de entradas ACL da identidade do objeto correspondente;
- SID: o SID de destino ao qual a permissão é concedida ou negada, links para a tabela ACL_SID
- MASK: a máscara de bit inteiro que representa a permissão real sendo concedida ou negada;
- CONCESSÃO: valor 1 significa concessão, valor 0 significa negação
AUDIT_SUCCESS e AUDIT_FAILURE: para fins de auditoria.

### 2.2. Dependência
Para poder usar Spring ACL em nosso projeto, vamos primeiro definir nossas dependências:

```
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-acl</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context-support</artifactId>
</dependency>
<dependency>
    <groupId>net.sf.ehcache</groupId>
    <artifactId>ehcache-core</artifactId>
    <version>2.6.11</version>
</dependency>
```

Spring ACL requer um cache para armazenar Object Identity e entradas ACL, então vamos usar o Ehcache aqui. E, para dar suporte ao Ehcache no Spring, também precisamos do spring-context-support.

Quando não estamos trabalhando com Spring Boot, precisamos adicionar versões explicitamente. Eles podem ser verificados no Maven Central: spring-security-acl, spring-security-config, spring-context-support, ehcache-core.

### 2.3. Configuração relacionada a ACL
Precisamos proteger todos os métodos que retornam objetos de domínio protegidos, ou fazem alterações no objeto, habilitando o Global Method Security:

```
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class AclMethodSecurityConfiguration 
  extends GlobalMethodSecurityConfiguration {

    @Autowired
    MethodSecurityExpressionHandler 
      defaultMethodSecurityExpressionHandler;

    @Override
    protected MethodSecurityExpressionHandler createExpressionHandler() {
        return defaultMethodSecurityExpressionHandler;
    }
}
```

Também vamos habilitar o controle de acesso baseado em expressão definindo prePostEnabled como true para usar Spring Expression Language (SpEL). Além disso, precisamos de um manipulador de expressão com suporte a ACL:

```
@Bean
public MethodSecurityExpressionHandler 
  defaultMethodSecurityExpressionHandler() {
    DefaultMethodSecurityExpressionHandler expressionHandler
      = new DefaultMethodSecurityExpressionHandler();
    AclPermissionEvaluator permissionEvaluator 
      = new AclPermissionEvaluator(aclService());
    expressionHandler.setPermissionEvaluator(permissionEvaluator);
    return expressionHandler;
}
```

Portanto, atribuímos AclPermissionEvaluator ao DefaultMethodSecurityExpressionHandler. O avaliador precisa de um MutableAclService para carregar configurações de permissão e definições de objeto de domínio do banco de dados.

Para simplificar, usamos o JdbcMutableAclService fornecido:

```
@Bean 
public JdbcMutableAclService aclService() { 
    return new JdbcMutableAclService(
      dataSource, lookupStrategy(), aclCache()); 
}
```

Como seu nome, o JdbcMutableAclService usa JDBCTemplate para simplificar o acesso ao banco de dados. Ele precisa de um DataSource (para JDBCTemplate), LookupStrategy (fornece uma pesquisa otimizada ao consultar o banco de dados) e um AclCache (cache de entradas ACL e identidade de objeto).

Novamente, para simplificar, usamos BasicLookupStrategy e EhCacheBasedAclCache fornecidos.

```
@Autowired
DataSource dataSource;

@Bean
public AclAuthorizationStrategy aclAuthorizationStrategy() {
    return new AclAuthorizationStrategyImpl(
      new SimpleGrantedAuthority("ROLE_ADMIN"));
}

@Bean
public PermissionGrantingStrategy permissionGrantingStrategy() {
    return new DefaultPermissionGrantingStrategy(
      new ConsoleAuditLogger());
}

@Bean
public EhCacheBasedAclCache aclCache() {
    return new EhCacheBasedAclCache(
      aclEhCacheFactoryBean().getObject(), 
      permissionGrantingStrategy(), 
      aclAuthorizationStrategy()
    );
}

@Bean
public EhCacheFactoryBean aclEhCacheFactoryBean() {
    EhCacheFactoryBean ehCacheFactoryBean = new EhCacheFactoryBean();
    ehCacheFactoryBean.setCacheManager(aclCacheManager().getObject());
    ehCacheFactoryBean.setCacheName("aclCache");
    return ehCacheFactoryBean;
}

@Bean
public EhCacheManagerFactoryBean aclCacheManager() {
    return new EhCacheManagerFactoryBean();
}

@Bean 
public LookupStrategy lookupStrategy() { 
    return new BasicLookupStrategy(
      dataSource, 
      aclCache(), 
      aclAuthorizationStrategy(), 
      new ConsoleAuditLogger()
    ); 
}
```

Aqui, a AclAuthorizationStrategy é responsável por concluir se um usuário atual possui todas as permissões necessárias em determinados objetos ou não.

Ele precisa do suporte de PermissionGrantingStrategy, que define a lógica para determinar se uma permissão é concedida a um determinado SID.

# 3. Método de segurança com Spring ACL
Até agora, fizemos todas as configurações necessárias. Agora podemos colocar a regra de verificação necessária em nossos métodos seguros.

Por padrão, Spring ACL se refere à classe BasePermission para todas as permissões disponíveis. Basicamente, temos uma permissão de LEITURA, ESCRITA, CRIAÇÃO, EXCLUIR e ADMINISTRAÇÃO.

Vamos tentar definir algumas regras de segurança:

```
@PostFilter("hasPermission(filterObject, 'READ')")
List<NoticeMessage> findAll();
    
@PostAuthorize("hasPermission(returnObject, 'READ')")
NoticeMessage findById(Integer id);
    
@PreAuthorize("hasPermission(#noticeMessage, 'WRITE')")
NoticeMessage save(@Param("noticeMessage")NoticeMessage noticeMessage);
```

Após a execução do método findAll(), @PostFilter será disparado. A regra exigida hasPermission (filterObject, ‘READ '), significa retornar apenas aqueles NoticeMessage para os quais o usuário atual tem permissão READ.

Da mesma forma, @PostAuthorize é disparado após a execução do método findById(), certifique-se de retornar apenas o objeto NoticeMessage se o usuário atual tiver permissão READ nele. Caso contrário, o sistema lançará uma AccessDeniedException.

Por outro lado, o sistema dispara a anotação @PreAuthorize antes de invocar o método save(). Ele decidirá onde o método correspondente pode ser executado ou não. Caso contrário, AccessDeniedException será lançado.

# 4. Em ação
Agora vamos testar todas essas configurações usando JUnit. Usaremos o banco de dados H2 para manter a configuração o mais simples possível.

Precisamos adicionar:

```
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
</dependency>

<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-test</artifactId>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
```

### 4.1. O cenário
Neste cenário, teremos dois usuários (gerente, hr) e uma função de usuário (ROLE_EDITOR), então nosso acl_sid será:

```
INSERT INTO acl_sid (id, principal, sid) VALUES
  (1, 1, 'manager'),
  (2, 1, 'hr'),
  (3, 0, 'ROLE_EDITOR');
```
Então, precisamos declarar a classe NoticeMessage em acl_class. E três instâncias da classe NoticeMessage serão inseridas em system_message.

Além disso, os registros correspondentes para essas 3 instâncias devem ser declarados em acl_object_identity:

```
INSERT INTO acl_class (id, class) VALUES
  (1, 'com.baeldung.acl.persistence.entity.NoticeMessage');

INSERT INTO system_message(id,content) VALUES 
  (1,'First Level Message'),
  (2,'Second Level Message'),
  (3,'Third Level Message');

INSERT INTO acl_object_identity 
  (id, object_id_class, object_id_identity, 
  parent_object, owner_sid, entries_inheriting) 
  VALUES
  (1, 1, 1, NULL, 3, 0),
  (2, 1, 2, NULL, 3, 0),
  (3, 1, 3, NULL, 3, 0);
```

Inicialmente, concedemos permissões READ e WRITE no primeiro objeto (id = 1) para o gerenciador de usuários. Enquanto isso, qualquer usuário com ROLE_EDITOR terá permissão READ em todos os três objetos, mas apenas possuirá permissão WRITE no terceiro objeto (id = 3). Além disso, o usuário hr terá apenas permissão de LEITURA no segundo objeto.

Aqui, como usamos a classe Spring ACL BasePermission padrão para verificação de permissão, o valor da máscara da permissão READ será 1 e o valor da máscara da permissão WRITE será 2. Nossos dados em acl_entry serão:

```
INSERT INTO acl_entry 
  (id, acl_object_identity, ace_order, 
  sid, mask, granting, audit_success, audit_failure) 
  VALUES
  (1, 1, 1, 1, 1, 1, 1, 1),
  (2, 1, 2, 1, 2, 1, 1, 1),
  (3, 1, 3, 3, 1, 1, 1, 1),
  (4, 2, 1, 2, 1, 1, 1, 1),
  (5, 2, 2, 3, 1, 1, 1, 1),
  (6, 3, 1, 3, 1, 1, 1, 1),
  (7, 3, 2, 3, 2, 1, 1, 1);
```

### 4.2. Caso de teste
Em primeiro lugar, tentamos chamar o método findAll.

Como nossa configuração, o método retorna apenas aquelas NoticeMessage nas quais o usuário tem permissão READ.

Portanto, esperamos que a lista de resultados contenha apenas a primeira mensagem:

```
@Test
@WithMockUser(username = "manager")
public void 
  givenUserManager_whenFindAllMessage_thenReturnFirstMessage(){
    List<NoticeMessage> details = repo.findAll();
 
    assertNotNull(details);
    assertEquals(1,details.size());
    assertEquals(FIRST_MESSAGE_ID,details.get(0).getId());
}
```

Em seguida, tentamos chamar o mesmo método com qualquer usuário que tenha a função - ROLE_EDITOR. Observe que, neste caso, esses usuários têm a permissão READ em todos os três objetos.

Portanto, esperamos que a lista de resultados contenha as três mensagens:

```
@Test
@WithMockUser(roles = {"EDITOR"})
public void 
  givenRoleEditor_whenFindAllMessage_thenReturn3Message(){
    List<NoticeMessage> details = repo.findAll();
    
    assertNotNull(details);
    assertEquals(3,details.size());
}
```

A seguir, usando o usuário manager, tentaremos obter a primeira mensagem por id e atualizar seu conteúdo - o que deve funcionar bem:

```
@Test
@WithMockUser(username = "manager")
public void 
  givenUserManager_whenFind1stMessageByIdAndUpdateItsContent_thenOK(){
    NoticeMessage firstMessage = repo.findById(FIRST_MESSAGE_ID);
    assertNotNull(firstMessage);
    assertEquals(FIRST_MESSAGE_ID,firstMessage.getId());
        
    firstMessage.setContent(EDITTED_CONTENT);
    repo.save(firstMessage);
        
    NoticeMessage editedFirstMessage = repo.findById(FIRST_MESSAGE_ID);
 
    assertNotNull(editedFirstMessage);
    assertEquals(FIRST_MESSAGE_ID,editedFirstMessage.getId());
    assertEquals(EDITTED_CONTENT,editedFirstMessage.getContent());
}
```
