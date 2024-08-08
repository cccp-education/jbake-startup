<#include "header.ftl">

<#include "menu.ftl">

<#if (content.title)??>
    <div class="page-header">
        <h1><#escape x as x?xml>${content.title}</#escape></h1>
    </div>
<#else></#if>

    <div class="container mt-5 mb-5">
        <h4>A propos de moi</h4>
    <div class="row">
        <div class="col-md-6">
            <p>Absolument ! Voici un texte Lorem Ipsum d'environ 350 caractères :
                Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit...
            </p>
        </div>
    </div>
        <hr/>
    </div>
    <p>${content.body}</p>


<#include "footer.ftl">