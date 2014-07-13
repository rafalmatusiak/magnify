$ ->
  defaultStrengths =
    inPackage: 0.5
    packageImports: 0.1
    packageCalls: 0.1
    packageRuntimeCalls: 0.1
    imports: 0.01
    calls: 0.01
    runtimeCalls: 0.01

  defaultLinkColors =
    inPackage: "#cc0000"
    packageImports: "#babdb6"
    packageCalls: "#f7ad00"
    packageRuntimeCalls: "#fce94f"
    imports: "#d3d7df"
    calls: "#f7ad00"
    runtimeCalls: "#fce94f"

  defaultLinkWidths =
    inPackage: (link) -> 1.5
    packageImports: (link) -> Math.min(link.count / 10.0, 5)
    packageCalls: (link) -> Math.min(link.count / 10.0, 5)
    packageRuntimeCalls: (link) -> Math.min(link.count / 10.0, 5)
    imports: (link) -> 1
    calls: (link) -> Math.min(link.count / 10.0, 5)
    runtimeCalls: (link) -> Math.min(link.count / 10.0, 5)

  linkKindsWithArrowhead = ["package-calls", "package-runtime-calls", "calls", "runtime-calls"]

  makeSvg = (jsonAddress) ->
    width = $("#chart").width()
    height = $("#chart").height()

    badness = d3.scale.linear().domain([-1, 300]).range(["green", "red"])
    color = (d) ->
      badness(d["metric--lines-of-code"])

    strength = (link) ->
      switch link.kind
        when "in-package" then defaultStrengths.inPackage
        when "package-imports" then defaultStrengths.packageImports
        when "package-calls" then defaultStrengths.packageCalls
        when "package-runtime-calls" then defaultStrengths.packageRuntimeCalls
        when "imports" then defaultStrengths.imports
        when "calls" then defaultStrengths.calls
        when "runtime-calls" then defaultStrengths.runtimeCalls

    linkColor = (link) ->
      switch link.kind
        when "in-package" then defaultLinkColors.inPackage
        when "package-imports" then defaultLinkColors.packageImports
        when "package-calls" then defaultLinkColors.packageCalls
        when "package-runtime-calls" then defaultLinkColors.packageRuntimeCalls
        when "imports" then defaultLinkColors.imports
        when "calls" then defaultLinkColors.calls
        when "runtime-calls" then defaultLinkColors.runtimeCalls

    linkWidth = (link) ->
      switch link.kind
        when "in-package" then defaultLinkWidths.inPackage(link)
        when "package-imports" then defaultLinkWidths.packageImports(link)
        when "package-calls" then defaultLinkWidths.packageCalls(link)
        when "package-runtime-calls" then defaultLinkWidths.packageRuntimeCalls(link)
        when "imports" then defaultLinkWidths.imports(link)
        when "calls" then defaultLinkWidths.calls(link)
        when "runtime-calls" then defaultLinkWidths.runtimeCalls(link)

    pageRankSize = (d) -> 3 + Math.max(3, 100.0 * d["page-rank"])

    force = d3.layout.force()
      .charge(-120)
      .linkDistance(30)
      .linkStrength(strength)
      .size([width, height])
      .gravity(0.2)

    scale = 1

    isLabelDisplayable = (d) -> scale >= 3

    labelDisplay = (d) -> if isLabelDisplayable(d) then "" else "none"

    label = {}

    svg = d3
      .select("#chart")
      .append("svg:svg")
      .attr("width", width)
      .attr("height", height)
      .attr("pointer-events", "all")
      .append("svg:g")
      .call(d3.behavior.zoom().on("zoom", ->
        scale = d3.event.scale
        svg.attr("transform", "translate(#{d3.event.translate}) scale(#{d3.event.scale})")
        label.style("display", labelDisplay)
      ))
      .append("svg:g")

    svg
      .append("svg:rect")
      .attr("width", width)
      .attr("height", height)
      .attr("fill", "transparent")
      .attr("pointer-events", "all")

    d3.json jsonAddress, (json) ->
      force
        .nodes(json.nodes)
        .links(json.edges)
        .start()

      link = svg.selectAll("line.link")
        .data(json.edges)
        .enter()
        .append("svg:line")
        .attr("class", "link")
        .style("stroke-width", linkWidth)
        .style("stroke", linkColor)
        .attr("marker-end", (d) -> if (d.source != d.target) then "url(##{d.kind})" else "")

      svg.append("svg:defs").selectAll("marker")
        .data(linkKindsWithArrowhead)
        .enter().append("svg:marker")
        .attr("id", (d) -> d )
        .attr("viewBox", "0 0 10 10")
        .attr("refX", 5)
        .attr("refY", 5)
        .attr("markerWidth", 4)
        .attr("markerHeight", 3)
        .attr("orient", "auto")
        .style("fill", "#000000")
        .append("svg:path")
        .attr("d", "M 0 0 L 10 5 L 0 10 z")

      linkedByIndex = {}
      json.edges.forEach((d) -> linkedByIndex[d.source.index + "," + d.target.index] = 1)

      isConnected = (a, b) ->
        linkedByIndex[a.index + "," + b.index] || linkedByIndex[b.index + "," + a.index] || a.index == b.index

      neighbours = (d, isRelated) ->
        json.edges
        .filter((link) -> (link.source.index == d.index || link.target.index == d.index) && isRelated(link))
        .map((link) -> if link.source.index == d.index then link.target else link.source)

      closure = (d, relation) ->
        related = []
        nextRelated = [d]
        while (nextRelated.length > 0)
          nextRelated = [].concat.apply([], nextRelated.map((n) -> relation(n))).filter((n) -> n not in related)
          related = related.concat nextRelated
        related

      blendColor = (c1, c2, alpha) ->
        rgb1 = d3.rgb(c1)
        rgb2 = d3.rgb(c2)
        d3.rgb(rgb1.r * alpha + rgb2.r * (1 - alpha), rgb1.g * alpha + rgb2.g * (1 - alpha), rgb1.b * alpha + rgb2.b * (1 - alpha)).toString()

      showReferenced = (opacity, detectCycle) ->
        (d) ->
          directReference = (d) -> neighbours(d, (link) -> link.source.index == d.index && link.target.index != d.index)
          directlyReferenced = directReference(d)
          referenced = closure(d, directReference)
          link
          .transition().duration(750).style("opacity", (o) -> if opacity is 1 || o.source in referenced && o.target in referenced || o.source is d then 1 else 0)
          label
          .transition().duration(750).style("opacity", (o) -> if opacity is 1 || o in referenced || o is d then 1 else 0)
          node
          .transition().duration(750).style("opacity", (o) -> if opacity is 1 || o in referenced || o is d then 1 else 0)
          .transition().duration(750).style("fill", (o) ->
            if opacity is 1 || o in directlyReferenced || o is d then color(o) else blendColor(color(o), $("body").css("background"), opacity)
          )
          .transition().duration(750).style("stroke", (o) ->
            if not detectCycle || not (o is d) then "" else if d in referenced then "#ff0000" else "#00ff00"
          )

      node = svg.selectAll("circle.node")
        .data(json.nodes)
        .enter()
        .append("circle")
        .attr("class", "node")
        .attr("r", pageRankSize)
        .style("fill", color)
        .call(force.drag)
        .on("mouseover", showReferenced(.1, true))
        .on("mouseout", showReferenced(1, false))

      node
        .append("title")
        .text((d) -> d.name)

      label = svg.selectAll("text.node-label")
        .data(json.nodes)
        .enter().append("text")
        .attr("class", "node-label")
        .attr("text-anchor", "middle")
        .attr("dy", ".7em")
        .attr("dx", "3em")
        .style("display", labelDisplay)
        .text((d) -> d.name)

      svg
        .style("opacity", 1e-6)
        .transition()
        .duration(1000)
        .style("opacity", 1)

      force.on "tick", ->
        link
          .attr("x1", (d) -> d.source.x)
          .attr("y1", (d) -> d.source.y)
          .attr("x2", (d) ->
            if (d.kind not in linkKindsWithArrowhead or d.target.x == d.source.x)
              ox = 0
            else
              dx = d.target.x - d.source.x
              dy = d.target.y - d.source.y
              dr = Math.sqrt(dx * dx + dy * dy)
              ox = (dx * (linkWidth(d) + 2*parseFloat(node.style("stroke-width")) + pageRankSize(d.target))) / dr
            d.target.x - ox
          )
          .attr("y2", (d) ->
            if (d.kind not in linkKindsWithArrowhead or d.target.y == d.source.y)
              oy = 0
            else
              dx = d.target.x - d.source.x
              dy = d.target.y - d.source.y
              dr = Math.sqrt(dx * dx + dy * dy)
              oy = (dy * (linkWidth(d) + 2*parseFloat(node.style("stroke-width")) + pageRankSize(d.target))) / dr
            d.target.y - oy
          )
        node
          .attr("cx", (d) -> d.x)
          .attr("cy", (d) -> d.y)
        label
          .attr("x", (d) -> d.x)
          .attr("y", (d) -> d.y)

  customSvg = (jsonAddress) ->
    width = $("#chart").width()
    height = $("#chart").height()

    badness = d3.scale.linear().domain([-1, 300]).range(["green", "red"])

    strengths =
      inPackage: defaultStrengths.inPackage
      packageImports: defaultStrengths.packageImports
      packageCalls: defaultStrengths.packageCalls
      packageRuntimeCalls: defaultStrengths.packageRuntimeCalls
      imports: defaultStrengths.imports
      calls: defaultStrengths.calls
      runtimeCalls: defaultStrengths.runtimeCalls
    strength = (link) ->
      switch link.kind
        when "in-package" then strengths.inPackage
        when "package-imports" then strengths.packageImports
        when "package-calls" then strengths.packageCalls
        when "package-runtime-calls" then strengths.packageRuntimeCalls
        when "imports" then strengths.imports
        when "calls" then strengths.calls
        when "runtime-calls" then strengths.runtimeCalls

    linkColors =
      inPackage: "transparent"
      packageImports: "transparent"
      packageCalls: "transparent"
      packageRuntimeCalls: "transparent"
      imports: "transparent"
      calls: "transparent"
      runtimeCalls: "transparent"
    linkColor = (link) ->
      switch link.kind
        when "in-package" then linkColors.inPackage
        when "package-imports" then linkColors.packageImports
        when "package-calls" then linkColors.packageCalls
        when "package-runtime-calls" then linkColors.packageRuntimeCalls
        when "imports" then linkColors.imports
        when "calls" then linkColors.calls
        when "runtime-calls" then linkColors.runtimeCalls

    linkWidth = (link) ->
      switch link.kind
        when "in-package" then defaultLinkWidths.inPackage(link)
        when "package-imports" then defaultLinkWidths.packageImports(link)
        when "package-calls" then defaultLinkWidths.packageCalls(link)
        when "package-runtime-calls" then defaultLinkWidths.packageRuntimeCalls(link)
        when "imports" then defaultLinkWidths.imports(link)
        when "calls" then defaultLinkWidths.calls(link)
        when "runtime-calls" then defaultLinkWidths.runtimeCalls(link)

    locColor = (d) -> badness(d["metric--lines-of-code"])
    uniformColor = (d) -> "#000000"

    defaultNodeColors =
      package: locColor
      class: locColor
    nodeColors =
      package: uniformColor
      class: uniformColor
    nodeColor = (node) ->
      switch node.kind
        when "package" then nodeColors.package(node)
        when "class" then nodeColors.class(node)

    pageRankSize = (d) -> 3 + Math.max(3, 100.0 * d["page-rank"])
    constantSize = (d) -> 5

    defaultNodeSizes =
      package: pageRankSize
      class: pageRankSize
    nodeSizes =
      package: constantSize
      class: constantSize
    nodeSize = (node) ->
      switch node.kind
        when "package" then nodeSizes.package(node)
        when "class" then nodeSizes.class(node)

    force = d3.layout.force()
    .charge(-120)
    .linkDistance(30)
    .linkStrength(0)
    .size([width, height])
    .gravity(0.2)

    scale = 1

    isLabelDisplayable = (d) -> scale >= 3

    labelDisplay = (d) -> if isLabelDisplayable(d) then "" else "none"

    label = {}

    svg = d3
    .select("#chart")
    .append("svg:svg")
    .attr("width", width)
    .attr("height", height)
    .attr("pointer-events", "all")
    .append("svg:g")
    .call(d3.behavior.zoom().on("zoom", ->
      scale = d3.event.scale
      svg.attr("transform", "translate(#{d3.event.translate}) scale(#{d3.event.scale})")
      label.style("display", labelDisplay)
    ))
    .append("svg:g")

    svg
    .append("svg:rect")
    .attr("width", width)
    .attr("height", height)
    .attr("fill", "transparent")
    .attr("pointer-events", "all")

    d3.json jsonAddress, (json) ->
      force
      .nodes(json.nodes)
      .links(json.edges)
      .start()

      linkMarkerEnd = (d) -> if (linkColor(d) != "transparent" and d.source != d.target) then "url(##{d.kind})" else ""

      link = svg.selectAll("line.link")
      .data(json.edges)
      .enter()
      .append("svg:line")
      .attr("class", "link")
      .style("stroke-width", linkWidth)
      .style("stroke", "transparent")
      .attr("marker-end", linkMarkerEnd)

      arrowhead = svg.append("svg:defs").selectAll("marker")
      .data(linkKindsWithArrowhead)
      .enter().append("svg:marker")
      .attr("id", (d) -> d )
      .attr("viewBox", "0 0 10 10")
      .attr("refX", 5)
      .attr("refY", 5)
      .attr("markerWidth", 4)
      .attr("markerHeight", 3)
      .attr("orient", "auto")
      .style("fill", "#000000")
      .append("svg:path")
      .attr("d", "M 0 0 L 10 5 L 0 10 z")

      check = (selector, attr) ->
        $(selector).on "click", ->
          if ($(this).is(":checked"))
            linkColors[attr] = defaultLinkColors[attr]
            strengths[attr] = defaultStrengths[attr]
          else
            linkColors[attr] = "transparent"
            strengths[attr] = 0
          link.attr("marker-end", linkMarkerEnd)
          link.style("stroke", linkColor)
          force.linkStrength(strength).start()
        $(selector).triggerHandler("click")
      check(".check-contains", "inPackage")
      check(".check-imports", "packageImports")
      check(".check-calls", "packageCalls")
      check(".check-runtime-calls", "packageRuntimeCalls")
      check(".check-imports", "imports")
      check(".check-calls", "calls")
      check(".check-runtime-calls", "runtimeCalls")

      linkedByIndex = {}
      json.edges.forEach((d) -> linkedByIndex[d.source.index + "," + d.target.index] = 1)

      isConnected = (a, b) ->
        linkedByIndex[a.index + "," + b.index] || linkedByIndex[b.index + "," + a.index] || a.index == b.index

      neighbours = (d, isRelated) ->
        json.edges
        .filter((link) -> (link.source.index == d.index || link.target.index == d.index) && isRelated(link))
        .map((link) -> if link.source.index == d.index then link.target else link.source)

      closure = (d, relation) ->
        related = []
        nextRelated = [d]
        while (nextRelated.length > 0)
          nextRelated = [].concat.apply([], nextRelated.map((n) -> relation(n))).filter((n) -> n not in related)
          related = related.concat nextRelated
        related

      blendColor = (c1, c2, alpha) ->
        rgb1 = d3.rgb(c1)
        rgb2 = d3.rgb(c2)
        d3.rgb(rgb1.r * alpha + rgb2.r * (1 - alpha), rgb1.g * alpha + rgb2.g * (1 - alpha), rgb1.b * alpha + rgb2.b * (1 - alpha)).toString()

      showReferenced = (opacity, detectCycle) ->
        (d) ->
          directReference = (d) -> neighbours(d, (link) -> link.source.index == d.index && link.target.index != d.index && not (linkColor(link) is "transparent"))
          directlyReferenced = directReference(d)
          referenced = closure(d, directReference)
          link
          .transition().duration(750).style("opacity", (o) -> if opacity is 1 || o.source in referenced && o.target in referenced || o.source is d then 1 else 0)
          label
          .transition().duration(750).style("opacity", (o) -> if opacity is 1 || o in referenced || o is d then 1 else 0)
          node
          .transition().duration(750).style("opacity", (o) -> if opacity is 1 || o in referenced || o is d then 1 else 0)
          .transition().duration(750).style("fill", (o) ->
            if opacity is 1 || o in directlyReferenced || o is d then nodeColor(o) else blendColor(nodeColor(o), $("body").css("background"), opacity)
          )
          .transition().duration(750).style("stroke", (o) ->
            if not detectCycle || not (o is d) then "" else if d in referenced then "#ff0000" else "#00ff00"
          )

      node = svg.selectAll("circle.node")
      .data(json.nodes)
      .enter()
      .append("circle")
      .attr("class", "node")
      .attr("r", 5)
      .style("fill", "#000000")
      .call(force.drag)
      .on("mouseover", showReferenced(.1, true))
      .on("mouseout", showReferenced(1, false))

      label = svg.selectAll("text.node-label")
      .data(json.nodes)
      .enter().append("text")
      .attr("class", "node-label")
      .attr("text-anchor", "middle")
      .attr("dy", ".7em")
      .attr("dx", "3em")
      .style("display", labelDisplay)
      .text((d) -> d.name)

      selectNodeColor = (input, kind) ->
        $("""input[name="#{input}"]""").on "click", ->
          $this = $(this)
          if ($this.is(":checked") and $this.attr("value") == "black")
            nodeColors[kind] = uniformColor
          else
            nodeColors[kind] = defaultNodeColors[kind]
          node.style("fill", nodeColor).call(force.drag)
        $("""input[name="#{input}"]""").triggerHandler("click")
      selectNodeColor("package-node-color", "package")
      selectNodeColor("class-node-color", "class")

      selectNodeSize = (input, kind) ->
        $("""input[name="#{input}"]""").on "click", ->
          $this = $(this)
          if ($this.is(":checked") and $this.attr("value") == "constant")
            nodeSizes[kind] = constantSize
          else
            nodeSizes[kind] = defaultNodeSizes[kind]
          node.attr("r", nodeSize).call(force.drag)
          force.linkStrength(strength).start()
        $("""input[name="#{input}"]""").triggerHandler("click")
      selectNodeSize("package-node-size", "package")
      selectNodeSize("class-node-size", "class")

      node
      .append("title")
      .text((d) -> d.name)

      svg
      .style("opacity", 1e-6)
      .transition()
      .duration(1000)
      .style("opacity", 1)

      force.on "tick", ->
        link
        .attr("x1", (d) -> d.source.x)
        .attr("y1", (d) -> d.source.y)
        .attr("x2", (d) ->
          if (d.kind not in linkKindsWithArrowhead or d.target.x == d.source.x)
            ox = 0
          else
            dx = d.target.x - d.source.x
            dy = d.target.y - d.source.y
            dr = Math.sqrt(dx * dx + dy * dy)
            ox = (dx * (linkWidth(d) + 2*parseFloat(node.style("stroke-width")) + nodeSizes[d.target.kind](d.target))) / dr
          d.target.x - ox
        )
        .attr("y2", (d) ->
          if (d.kind not in linkKindsWithArrowhead or d.target.y == d.source.y)
            oy = 0
          else
            dx = d.target.x - d.source.x
            dy = d.target.y - d.source.y
            dr = Math.sqrt(dx * dx + dy * dy)
            oy = (dy * (linkWidth(d) + 2*parseFloat(node.style("stroke-width")) + nodeSizes[d.target.kind](d.target))) / dr
          d.target.y - oy
        )
        node
        .attr("cx", (d) -> d.x)
        .attr("cy", (d) -> d.y)
        label
        .attr("x", (d) -> d.x)
        .attr("y", (d) -> d.y)

  clearSvg = ->
    $("#chart").empty()

  checkOptimized = (jsonAddress, custom) ->
    $(".check-optimized").unbind("click.svg")
    $(".check-optimized").on "click.svg", ->
      clearSvg()
      if ($(".check-optimized").is(":checked"))
        versionJsonAddress = jsonAddress
      else
        versionJsonAddress = "0/" + jsonAddress
      if (custom)
        $(".check-packages").triggerHandler("click")
      else
        makeSvg(versionJsonAddress)
    $(".check-optimized").triggerHandler("click")

  $(".custom-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-custom-tab").addClass("active")
    $(".gauges").remove()
    $(".mag-sidenav .active").after(
      """
      <li class="active gauges">
        <a href="#">
          <form>
            <div class="table-responsive">
            <table class="table" style="table-layout: fixed; _word-wrap: break-word;">
              <caption><strong>Nodes</strong></caption>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Size</th>
                  <th>Color</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>
                    <label class="checkbox inline">
                      <input type="checkbox" value="" class="check-packages" checked="checked" />
                      packages
                    </label>
                  </td>
                  <td>
                    <div class="controls">
                      <ul class="nav nav-pills left">
                        <li class="dropdown span12">
                          <a class="dropdown-toggle" data-toggle="dropdown">
                          <span class="dropdown-title">Constant</span><span class="caret"></span>
                          </a>
                          <ul class="dropdown-menu package-node-size">
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="package-node-size" value="constant" checked="checked" style="display: none"/>
                                Constant
                              </label>
                            </a></li>
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="package-node-size" value="page-rank" style="display: none"/>
                                Page rank
                              </label></a>
                            </a></li>
                          </ul>
                        </li>
                      </ul>
                    </div>
                  </td>
                  <td>
                    <div class="controls">
                      <ul class="nav nav-pills left">
                        <li class="dropdown span12">
                          <a class="dropdown-toggle" data-toggle="dropdown">
                          <span class="dropdown-title">Black</span><span class="caret"></span>
                          </a>
                          <ul class="dropdown-menu package-node-color">
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="package-node-color" value="black" checked="checked" style="display: none"/>
                                Black
                              </label>
                            </a></li>
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="package-node-color" value="by-avg-loc" style="display: none"/>
                                Avg. lines of code / class
                              </label>
                            </a></li>
                          </ul>
                        </li>
                      </ul>
                    </div>
                  </td>
                </tr>
                <tr>
                  <td>
                    <label class="checkbox inline">
                      <input type="checkbox" value="" class="check-classes"/>
                      classes
                    </label>
                  </td>
                  <td>
                    <div class="controls">
                      <ul class="nav nav-pills left">
                        <li class="dropdown span12">
                          <a class="dropdown-toggle" data-toggle="dropdown">
                          <span class="dropdown-title">Constant</span><span class="caret"></span>
                          </a>
                          <ul class="dropdown-menu class-node-size">
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="class-node-size" value="constant" checked="checked" style="display: none"/>
                                Constant
                              </label>
                            </a></li>
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="class-node-size" value="page-rank" style="display: none"/>
                                Page rank
                              </label></a>
                            </a></li>
                          </ul>
                        </li>
                      </ul>
                    </div>
                  </td>
                  <td>
                    <div class="controls">
                      <ul class="nav nav-pills left">
                        <li class="dropdown span12">
                          <a class="dropdown-toggle" data-toggle="dropdown">
                          <span class="dropdown-title">Black</span><span class="caret"></span>
                          </a>
                          <ul class="dropdown-menu class-node-color">
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="class-node-color" value="black" checked="checked" style="display: none"/>
                                Black
                              </label>
                            </a></li>
                            <li><a>
                              <label class="radio">
                                <input type="radio" name="class-node-color" value="by-avg-loc" style="display: none"/>
                                Avg. lines of code / class
                              </label>
                            </a></li>
                          </ul>
                        </li>
                      </ul>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            </div>
            <div class="table-responsive">
            <table class="table" style="table-layout: fixed; _word-wrap: break-word;">
              <caption><strong>Edges</strong></caption>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Width</th>
                  <th>Color</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>
                    <label class="checkbox inline">
                      <input type="checkbox" value="" class="check-contains"/>
                      contains
                    </label>
                  </td>
                  <td>
                    <label class="checkbox inline">
                      Constant
                    </label>
                  </td>
                  <td style="background-color: #{defaultLinkColors.inPackage}">
                  </td>
                </tr>
                <tr>
                  <td>
                    <label class="checkbox inline">
                      <input type="checkbox" value="" class="check-imports"/>
                      imports
                    </label>
                  </td>
                  <td>
                    <label class="checkbox inline">
                      Count
                    </label>
                  </td>
                  <td style="background-color: #{defaultLinkColors.imports}">
                  </td>
                </tr>
                <tr>
                  <td>
                    <label class="checkbox inline">
                      <input type="checkbox" value="" class="check-calls"/>
                      calls
                    </label>
                  </td>
                  <td>
                    <label class="checkbox inline">
                      Count
                    </label>
                  </td>
                  <td style="background-color: #{defaultLinkColors.calls}">
                  </td>
                </tr>
                <tr>
                  <td>
                    <label class="checkbox inline">
                      <input type="checkbox" value="" class="check-runtime-calls"/>
                      runtime calls
                    </label>
                  </td>
                  <td>
                    <label class="checkbox inline">
                      Count
                    </label>
                  </td>
                  <td style="background-color: #{defaultLinkColors.runtimeCalls}">
                  </td>
                </tr>
              </tbody>
            </table>
            </div>
          </form>
        </a>
      </li>
      <li class="active gauges">
        <a href="javascript:;">
          <label class="control-label pagination-centered"><strong>Optimize</strong></label>
          <form class="form">
            <div class="control-group">
              <label class="control-label" for="iterations">Iterations</label>
              <div class="controls">
                <input class="span6 iterations" type="text" name="iterations" placeholder="100">
              </div>
            </div>
            <div class="control-group">
              <label class="control-label" for="tolerance">Tolerance</label>
              <div class="controls">
                <input class="span6 tolerance" type="text" name="tolerance" placeholder="5">
              </div>
            </div>
            <div class="control-group">
              <div class="controls">
                <label class="checkbox inline">
                  <input type="checkbox" value="" class="check-incremental"/>
                  incremental
                </label>
              </div>
            </div>
            <div class="control-group">
              <div class="controls">
                <span class="btn btn-default optimize-button">Go!</span>
              </div>
            </div>
          </form>
          <label class="optimization-status" />
        </a>
      </li>
      """)
    checkNodeKind = (selector) ->
      $(selector).on "click", ->
        clearSvg()
        if ($(".check-optimized").is(":checked"))
          jsonAddress = ""
        else
          jsonAddress = "0/"
        if ($(".check-packages").is(":checked") && $(".check-classes").is(":checked"))
          jsonAddress += "custom.json"
        else if ($(".check-packages").is(":checked"))
          jsonAddress += "customPackages.json"
        else if ($(".check-classes").is(":checked"))
          jsonAddress += "customClasses.json"
        else
          jsonAddress = ""
        if (jsonAddress)
          customSvg(jsonAddress)
    checkNodeKind(".check-packages")
    checkNodeKind(".check-classes")

    selectDropdown = (selector) ->
      $("#{selector} li").on "click", ->
        $(this).parent().parent().find(".dropdown-title").text($(this).find("label").text())
    selectDropdown(".package-node-color")
    selectDropdown(".package-node-size")
    selectDropdown(".class-node-color")
    selectDropdown(".class-node-size")

    $(".iterations").val($(".iterations").attr("placeholder"))
    $(".tolerance").val($(".tolerance").attr("placeholder"))
    $(".optimize-button").on "click", ->
      jsRoutes.controllers.OptimizeGraph.optimize($("#projectName").text(), $(".iterations").val(), $(".tolerance").val(), $(".incremental").is(":checked")).ajax({
        success: (data) ->
          $(".optimization-status").removeClass((index, css) ->
            (css.match(/\btext-\S+/g) || []).join(' ')
          )
          $(".optimization-status").addClass("text-success")
          $(".optimization-status").text("")
          $(".check-optimized").prop("disabled", false)
          $(".check-optimized").prop("checked", true)
          $(".check-optimized").triggerHandler("click")
        error: (data) ->
          $(".optimization-status").removeClass((index, css) ->
            (css.match(/\btext-\S+/g) || []).join(' ')
          )
          $(".optimization-status").addClass("text-error")
          $(".optimization-status").text("Optimization failed!")
          $(".check-optimized").prop("checked", false)
          $(".check-optimized").prop("disabled", true)
          $(".check-optimized").triggerHandler("click")
      })
      $(".optimization-status").removeClass((index, css) ->
        (css.match(/\btext-\S+/g) || []).join(' ')
      )
      $(".optimization-status").addClass("text-warning")
      $(".optimization-status").text("Optimizing...")
    checkOptimized("customPackages.json", true)

  $(".packages-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-packages-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("packages.json", false)
    $("[rel='tooltip']").tooltip()

  $(".package-imports-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-package-imports-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("pkgImports.json", false)
    $("[rel='tooltip']").tooltip()

  $(".package-calls-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-package-calls-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("pkgCalls.json", false)
    $("[rel='tooltip']").tooltip()

  $(".class-calls-button").on "click", (event) ->
    $(".nav-graph-detail-level").find("*").removeClass("active")
    $(".nav-graph-class-calls-tab").addClass("active")
    $(".gauges").remove()
    checkOptimized("classCalls.json", false)
    $("[rel='tooltip']").tooltip()

  jsRoutes.controllers.ShowGraph.versionsJson($("#projectName").text()).ajax({
    success: (data) ->
      $(".check-optimized").prop("disabled", data.versions.length <= 1)
    error: (data) ->
      $(".check-optimized").prop("checked", false)
      $(".check-optimized").prop("disabled", true)
  })

  $(".packages-button").triggerHandler("click")
  $("[rel='tooltip']").tooltip()
