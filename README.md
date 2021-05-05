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
